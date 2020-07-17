package com.example.demo.controller;

import com.alibaba.fastjson.JSONObject;
import com.example.demo.util.JsonUtil;
import com.example.demo.util.KeyValue;
import com.example.demo.util.MyForm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.*;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.form.StartFormData;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricDetail;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.impl.persistence.entity.HistoricDetailVariableInstanceUpdateEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.image.ProcessDiagramGenerator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import sun.misc.BASE64Encoder;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

@Controller
@RequestMapping
public class MainController {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FormService formService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ProcessEngineConfiguration processEngineConfiguration;


    @RequestMapping("index")
    public String toIndex(org.springframework.ui.Model model) {

        List<Model> list = repositoryService.createModelQuery().list();

        model.addAttribute("list", list);
        return "index";
    }
    /**
     * 创建模型
     * @param request
     * @param response
     * @return
     */
    @RequestMapping("/createModel")
    public String createModel(HttpServletRequest request, HttpServletResponse response) {

        String name = "请假流程";
        String description = "这是一个请假流程";

        String id = null;
        try {
            Model model = repositoryService.newModel();
            String key = name;
            //版本号
            String revision = "1";
            ObjectNode modelNode = objectMapper.createObjectNode();
            modelNode.put(ModelDataJsonConstants.MODEL_NAME, name);
            modelNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, description);
            modelNode.put(ModelDataJsonConstants.MODEL_REVISION, revision);

            model.setName(name);
            model.setKey(key);
            //模型分类 结合自己的业务逻辑
            //model.setCategory(category);

            model.setMetaInfo(modelNode.toString());

            repositoryService.saveModel(model);
            id = model.getId();

            //完善ModelEditorSource
            ObjectNode editorNode = objectMapper.createObjectNode();
            editorNode.put("id", "canvas");
            editorNode.put("resourceId", "canvas");
            ObjectNode stencilSetNode = objectMapper.createObjectNode();
            stencilSetNode.put("namespace",
                    "http://b3mn.org/stencilset/bpmn2.0#");
            editorNode.put("stencilset", stencilSetNode);
            repositoryService.addModelEditorSource(id, editorNode.toString().getBytes("utf-8"));
            String aa = request.getContextPath();
            response.sendRedirect(request.getContextPath() + "/modeler.html?modelId=" + id);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "index";
    }


    /**
     * 部署流程
     * @param id
     * @return
     * @throws Exception
     */
    @RequestMapping("deploymentModel")
    @ResponseBody
    public com.alibaba.fastjson.JSONObject deploymentModel(String id) throws Exception {

        //获取模型
        Model modelData = repositoryService.getModel(id);
        byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());

        if (bytes == null) {
            return JsonUtil.getFailJson("模型数据为空，请先设计流程并成功保存，再进行发布。");
        }
        JsonNode modelNode = modelNode = new ObjectMapper().readTree(bytes);

        BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
        if (model.getProcesses().size() == 0) {
            return JsonUtil.getFailJson("数据模型不符要求，请至少设计一条主线流程。");
        }
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);

        //发布流程
        String processName = modelData.getName() + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name(modelData.getName())
                .addString(processName, new String(bpmnBytes, "UTF-8"))
                .deploy();
        modelData.setDeploymentId(deployment.getId());
        repositoryService.saveModel(modelData);
        return JsonUtil.getSuccessJson("流程发布成功");
    }

    /**
     * 加载流程
     * @param model
     * @return
     */
    @RequestMapping("startPage")
    public String startPage(org.springframework.ui.Model model) {
        //加载流程定义
        List<ProcessDefinition> list = repositoryService.createProcessDefinitionQuery().list();
        model.addAttribute("list", list);
        return "startPage";
    }

    /**
     * 开启这个流程界面
     * @param id
     * @param model
     * @return
     */
    @RequestMapping("startProcess/{id}")
    public String startProcess(@PathVariable("id") String id, org.springframework.ui.Model model) {

        //按照流程定义ID加载流程开启时候需要的表单信息
        StartFormData startFormData = formService.getStartFormData(id);
        List<FormProperty> formProperties = startFormData.getFormProperties();

        //流程定义ID
        model.addAttribute("processesId", id);
        model.addAttribute("form", formProperties);

        return "startProcess";
    }

    /**
     * 开始请假流程提交事件
     * @param param
     * @return
     */
    @RequestMapping("startProcesses")
    @ResponseBody
    public JSONObject startProcesses(@RequestParam Map<String, Object> param) {

        String processesId = (String) param.get("processesId");
        //流程提交人 这里模拟
        String userId = (String) param.get("userId");

        if (StringUtils.isEmpty(processesId)) {
            return JsonUtil.getFailJson("参数错误");
        }
        param.remove("processesId");

        ProcessInstance pi = runtimeService.startProcessInstanceById(processesId, userId, param);

        if (null == pi) {
            return JsonUtil.getFailJson("流程启动失败！");
        }
        return JsonUtil.getSuccessJson("启动流程成功！");

    }

    /**
     * 跳转至审批界面
     * @param id
     * @param model
     * @return
     */
    @RequestMapping("taskApproval/{id}")
    public String toTaskList(@PathVariable("id") String id, org.springframework.ui.Model model) {

        if (StringUtils.isNotEmpty(id)) {

            List<Task> list = taskService.createTaskQuery().taskAssignee(id).list();
            model.addAttribute("list", list);
        }

        return "taskApproval";
    }


    /**
     * 审批界面详情
     * @param id
     * @param model
     * @return
     */
    @RequestMapping("taskDetails/{taskId}")
    public String toTaskDetails(@PathVariable("taskId") String id, org.springframework.ui.Model model) {


        Map<String, Object> map = new HashMap<>();
        //当前任务
        Task task = this.taskService.createTaskQuery().taskId(id).singleResult();
        String processInstanceId = task.getProcessInstanceId();

        TaskFormData taskFormData = this.formService.getTaskFormData(id);
        List<FormProperty> list = taskFormData.getFormProperties();
        map.put("task", task);
        map.put("list", list);
        map.put("history", assembleProcessForm(processInstanceId));

        model.addAllAttributes(map);

        return "taskDetails";
    }

    public List<MyForm> assembleProcessForm(String processInstanceId) {

        List<HistoricActivityInstance> historys = historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId).list();

        List<MyForm> myform = new ArrayList<>();

        for (HistoricActivityInstance activity : historys) {

            String actInstanceId = activity.getId();
            MyForm form = new MyForm();
            form.setActName(activity.getActivityName());
            form.setAssignee(activity.getAssignee());
            form.setProcInstId(activity.getProcessInstanceId());
            form.setTaskId(activity.getTaskId());
            //查询表单信息

            List<KeyValue> maps = new LinkedList<>();

            List<HistoricDetail> processes = historyService.createHistoricDetailQuery().activityInstanceId(actInstanceId).list();
            for (HistoricDetail process : processes) {
                HistoricDetailVariableInstanceUpdateEntity pro = (HistoricDetailVariableInstanceUpdateEntity) process;

                KeyValue keyValue = new KeyValue();

                keyValue.setKey(pro.getName());
                keyValue.setValue(pro.getTextValue());

                maps.add(keyValue);
            }
            form.setProcess(maps);

            myform.add(form);
        }

        return myform;
    }

    @RequestMapping("completeTasks")
    @ResponseBody
    public JSONObject completeTasks(@RequestParam Map<String, Object> param) {


        String taskId = (String) param.get("taskId");

        if (StringUtils.isEmpty(taskId)) {
            return JsonUtil.getFailJson("参数错误");
        }
        param.remove("taskId");

        Task task = this.taskService.createTaskQuery().taskId(taskId).singleResult();

        taskService.complete(task.getId(), param);

        return JsonUtil.getSuccessJson("流程已确认");

    }

    @RequestMapping("generateProcessImg")
    @ResponseBody
    public JSONObject generateProcessImg(String processInstanceId) throws IOException {

        //获取历史流程实例
        HistoricProcessInstance processInstance = historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        //获取流程图
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());

        ProcessDiagramGenerator diagramGenerator = processEngineConfiguration.getProcessDiagramGenerator();
        ProcessDefinitionEntity definitionEntity = (ProcessDefinitionEntity) repositoryService.getProcessDefinition(processInstance.getProcessDefinitionId());

        List<HistoricActivityInstance> highLightedActivitList = historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId).list();
        //高亮环节id集合
        List<String> highLightedActivitis = new ArrayList<String>();

        //高亮线路id集合
        List<String> highLightedFlows = getHighLightedFlows(definitionEntity, highLightedActivitList);

        for (HistoricActivityInstance tempActivity : highLightedActivitList) {
            String activityId = tempActivity.getActivityId();
            highLightedActivitis.add(activityId);
        }
        //配置字体
        InputStream imageStream = diagramGenerator.generateDiagram(bpmnModel, "png", highLightedActivitis, highLightedFlows, "宋体", "微软雅黑", "黑体", null, 2.0);
        BufferedImage bi = ImageIO.read(imageStream);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", bos);
        //转换成字节
        byte[] bytes = bos.toByteArray();
        BASE64Encoder encoder = new BASE64Encoder();
        //转换成base64串
        String png_base64 = encoder.encodeBuffer(bytes);
        //删除 \r\n
        png_base64 = png_base64.replaceAll("\n", "").replaceAll("\r", "");
        //测试可以自己打印图片到本地查看
//        File file = new File("D:/3.png");
//        if (!file.exists()) {
//            try {
//                file.createNewFile();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        InputStream inputStream = new ByteArrayInputStream(bytes);
//        OutputStream outputStream = null;
//        outputStream = new FileOutputStream(file);
//
//
//        int len = 0;
//        byte[] in_b  = null;
//        byte[] buf = new byte[1024];
//        while ((len = inputStream.read(buf, 0, 1024)) != -1) {
//            outputStream.write(buf, 0, len);
//        }
//        outputStream.flush();

        bos.close();
        imageStream.close();
        return JsonUtil.getSuccessJson("success", png_base64);
    }



    public List<String> getHighLightedFlows(
            ProcessDefinitionEntity processDefinitionEntity,
            List<HistoricActivityInstance> historicActivityInstances) {

        // 用以保存高亮的线flowId
        List<String> highFlows = new ArrayList<String>();
        // 对历史流程节点进行遍历
        for (int i = 0; i < historicActivityInstances.size() - 1; i++) {
            // 得到节点定义的详细信息
            ActivityImpl activityImpl = processDefinitionEntity
                    .findActivity(historicActivityInstances.get(i)
                            .getActivityId());
            // 用以保存后需开始时间相同的节点
            List<ActivityImpl> sameStartTimeNodes = new ArrayList<ActivityImpl>();
            ActivityImpl sameActivityImpl1 = processDefinitionEntity
                    .findActivity(historicActivityInstances.get(i + 1)
                            .getActivityId());
            // 将后面第一个节点放在时间相同节点的集合里
            sameStartTimeNodes.add(sameActivityImpl1);
            for (int j = i + 1; j < historicActivityInstances.size() - 1; j++) {
                // 后续第一个节点
                HistoricActivityInstance activityImpl1 = historicActivityInstances
                        .get(j);
                // 后续第二个节点
                HistoricActivityInstance activityImpl2 = historicActivityInstances
                        .get(j + 1);
                // 如果第一个节点和第二个节点开始时间相同保存
                if (activityImpl1.getStartTime().equals(
                        activityImpl2.getStartTime())) {
                    ActivityImpl sameActivityImpl2 = processDefinitionEntity
                            .findActivity(activityImpl2.getActivityId());
                    sameStartTimeNodes.add(sameActivityImpl2);
                } else {
                    // 有不相同跳出循环
                    break;
                }
            }
            // 取出节点的所有出去的线
            List<PvmTransition> pvmTransitions = activityImpl
                    .getOutgoingTransitions();
            // 对所有的线进行遍历
            for (PvmTransition pvmTransition : pvmTransitions) {
                ActivityImpl pvmActivityImpl = (ActivityImpl) pvmTransition
                        .getDestination();
                // 如果取出的线的目标节点存在时间相同的节点里，保存该线的id，进行高亮显示
                if (sameStartTimeNodes.contains(pvmActivityImpl)) {
                    highFlows.add(pvmTransition.getId());
                }
            }
        }
        return highFlows;
    }


/**
 * 框架封装，节点高亮显示
 */
//    @RequestMapping(value="/process-instance/highlights")
//    @ResponseBody
//    public JSONObject getHighlighted( String processInstanceId) {
//
//        ObjectNode responseJSON = objectMapper.createObjectNode();
//
//        responseJSON.put("processInstanceId", processInstanceId);
//
//        ArrayNode activitiesArray = objectMapper.createArrayNode();
//        ArrayNode flowsArray = objectMapper.createArrayNode();
//
//        try {
//            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
//            ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) repositoryService.getProcessDefinition(processInstance.getProcessDefinitionId());
//
//            responseJSON.put("processDefinitionId", processInstance.getProcessDefinitionId());
//
//            List<String> highLightedActivities = runtimeService.getActiveActivityIds(processInstanceId);
//            List<String> highLightedFlows = getHighLightedFlows(processDefinition, processInstanceId);
//
//            for (String activityId : highLightedActivities) {
//                activitiesArray.add(activityId);
//            }
//
//            for (String flow : highLightedFlows) {
//                flowsArray.add(flow);
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        responseJSON.put("activities", activitiesArray);
//        responseJSON.put("flows", flowsArray);
//
//        return responseJSON;
//    }
//
//
//    /**
//     * getHighLightedFlows
//     *
//     * @param processDefinition
//     * @param processInstanceId
//     * @return
//     */
//    private List<String> getHighLightedFlows(ProcessDefinitionEntity processDefinition, String processInstanceId) {
//
//        List<String> highLightedFlows = new ArrayList<String>();
//
//        List<HistoricActivityInstance> historicActivityInstances = historyService.createHistoricActivityInstanceQuery()
//                .processInstanceId(processInstanceId)
//                //order by startime asc is not correct. use default order is correct.
//                //.orderByHistoricActivityInstanceStartTime().asc()/*.orderByActivityId().asc()*/
//                .list();
//
//        LinkedList<HistoricActivityInstance> hisActInstList = new LinkedList<HistoricActivityInstance>();
//        hisActInstList.addAll(historicActivityInstances);
//
//        getHighlightedFlows(processDefinition.getActivities(), hisActInstList, highLightedFlows);
//
//        return highLightedFlows;
//    }
//
//    /**
//     * getHighlightedFlows
//     *
//     * code logic:
//     * 1. Loop all activities by id asc order;
//     * 2. Check each activity's outgoing transitions and eventBoundery outgoing transitions, if outgoing transitions's destination.id is in other executed activityIds, add this transition to highLightedFlows List;
//     * 3. But if activity is not a parallelGateway or inclusiveGateway, only choose the earliest flow.
//     *
//     * @param activityList
//     * @param hisActInstList
//     * @param highLightedFlows
//     */
//    private void getHighlightedFlows(List<ActivityImpl> activityList, LinkedList<HistoricActivityInstance> hisActInstList, List<String> highLightedFlows){
//
//        //check out startEvents in activityList
//        List<ActivityImpl> startEventActList = new ArrayList<ActivityImpl>();
//        Map<String, ActivityImpl> activityMap = new HashMap<String, ActivityImpl>(activityList.size());
//        for(ActivityImpl activity : activityList){
//
//            activityMap.put(activity.getId(), activity);
//
//            String actType = (String) activity.getProperty("type");
//            if (actType != null && actType.toLowerCase().indexOf("startevent") >= 0){
//                startEventActList.add(activity);
//            }
//        }
//
//        //These codes is used to avoid a bug:
//        //ACT-1728 If the process instance was started by a callActivity, it will be not have the startEvent activity in ACT_HI_ACTINST table
//        //Code logic:
//        //Check the first activity if it is a startEvent, if not check out the startEvent's highlight outgoing flow.
//        HistoricActivityInstance firstHistActInst = hisActInstList.getFirst();
//        String firstActType = (String) firstHistActInst.getActivityType();
//        if (firstActType != null && firstActType.toLowerCase().indexOf("startevent") < 0){
//            PvmTransition startTrans = getStartTransaction(startEventActList, firstHistActInst);
//            if (startTrans != null){
//                highLightedFlows.add(startTrans.getId());
//            }
//        }
//
//        while (!hisActInstList.isEmpty()) {
//            HistoricActivityInstance histActInst = hisActInstList.removeFirst();
//            ActivityImpl activity = activityMap.get(histActInst.getActivityId());
//            if (activity != null) {
//                boolean isParallel = false;
//                String type = histActInst.getActivityType();
//                if ("parallelGateway".equals(type) || "inclusiveGateway".equals(type)){
//                    isParallel = true;
//                } else if ("subProcess".equals(histActInst.getActivityType())){
//                    getHighlightedFlows(activity.getActivities(), hisActInstList, highLightedFlows);
//                }
//
//                List<PvmTransition> allOutgoingTrans = new ArrayList<PvmTransition>();
//                allOutgoingTrans.addAll(activity.getOutgoingTransitions());
//                allOutgoingTrans.addAll(getBoundaryEventOutgoingTransitions(activity));
//                List<String> activityHighLightedFlowIds = getHighlightedFlows(allOutgoingTrans, hisActInstList, isParallel);
//                highLightedFlows.addAll(activityHighLightedFlowIds);
//            }
//        }
//    }
//
//    /**
//     * Check out the outgoing transition connected to firstActInst from startEventActList
//     *
//     * @param startEventActList
//     * @param firstActInst
//     * @return
//     */
//    private PvmTransition getStartTransaction(List<ActivityImpl> startEventActList, HistoricActivityInstance firstActInst){
//        for (ActivityImpl startEventAct: startEventActList) {
//            for (PvmTransition trans : startEventAct.getOutgoingTransitions()) {
//                if (trans.getDestination().getId().equals(firstActInst.getActivityId())) {
//                    return trans;
//                }
//            }
//        }
//        return null;
//    }
//
//    /**
//     * getBoundaryEventOutgoingTransitions
//     *
//     * @param activity
//     * @return
//     */
//    private List<PvmTransition> getBoundaryEventOutgoingTransitions(ActivityImpl activity){
//        List<PvmTransition> boundaryTrans = new ArrayList<PvmTransition>();
//        for(ActivityImpl subActivity : activity.getActivities()){
//            String type = (String)subActivity.getProperty("type");
//            if(type!=null && type.toLowerCase().indexOf("boundary")>=0){
//                boundaryTrans.addAll(subActivity.getOutgoingTransitions());
//            }
//        }
//        return boundaryTrans;
//    }
//
//    /**
//     * find out single activity's highlighted flowIds
//     *
//     * @param activity
//     * @param hisActInstList
//     * @param isExclusive if true only return one flowId(Such as exclusiveGateway, BoundaryEvent On Task)
//     * @return
//     */
//    private List<String> getHighlightedFlows(List<PvmTransition> pvmTransitionList, LinkedList<HistoricActivityInstance> hisActInstList, boolean isParallel){
//
//        List<String> highLightedFlowIds = new ArrayList<String>();
//
//        PvmTransition earliestTrans = null;
//        HistoricActivityInstance earliestHisActInst = null;
//
//        for (PvmTransition pvmTransition : pvmTransitionList) {
//
//            String destActId = pvmTransition.getDestination().getId();
//            HistoricActivityInstance destHisActInst = findHisActInst(hisActInstList, destActId);
//            if (destHisActInst != null) {
//                if (isParallel) {
//                    highLightedFlowIds.add(pvmTransition.getId());
//                } else if (earliestHisActInst == null || (earliestHisActInst.getId().compareTo(destHisActInst.getId()) > 0)) {
//                    earliestTrans = pvmTransition;
//                    earliestHisActInst = destHisActInst;
//                }
//            }
//        }
//
//        if ((!isParallel) && earliestTrans!=null){
//            highLightedFlowIds.add(earliestTrans.getId());
//        }
//
//        return highLightedFlowIds;
//    }
//
//    private HistoricActivityInstance findHisActInst(LinkedList<HistoricActivityInstance> hisActInstList, String actId){
//        for (HistoricActivityInstance hisActInst : hisActInstList){
//            if (hisActInst.getActivityId().equals(actId)){
//                return hisActInst;
//            }
//        }
//        return null;
//    }

    /**
     * 删除流程实例（未部署model）
     * @param modelId 模型ID
     * @param result
     * @return
     */
    @ResponseBody
    @RequestMapping("/delete")
    public JSONObject deleteProcessInstance(String modelId){
        Model modelData = repositoryService.getModel(modelId);
        if(null != modelData){
            try {
                this.repositoryService.deleteModel(modelId);
                return JsonUtil.getSuccessJson("删除成功");
            } catch (Exception e) {
                JsonUtil.getFailJson("删除失败");
            }
        }
        return JsonUtil.getSuccessJson("删除成功");
    }



    /**
     * 删除已部署实例
     * @param id
     * @return
     */
    @RequestMapping("deleteProcess")
    public JSONObject deleteProcess(String id) {

        ProcessDefinition pd = (ProcessDefinition)this.repositoryService.createProcessDefinitionQuery().processDefinitionId(id).singleResult();
        if(pd!=null){
            this.repositoryService.deleteDeployment(pd.getDeploymentId(), true);
            return JsonUtil.getSuccessJson("删除成功");

        }
    return JsonUtil.getFailJson("删除失败");
    }
}
