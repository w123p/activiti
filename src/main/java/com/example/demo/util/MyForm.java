package com.example.demo.util;

import java.util.List;

/**
 * 组装审批表单信息
 */
public class MyForm {


    //任务名称
    private String actName;

    //派遣人
    private String assignee;


    //流程实例ID
    private String procInstId;


    //任务ID
    private String taskId;

    //表单属性
    private List<KeyValue> process;


    public String getActName() {
        return actName;
    }

    public void setActName(String actName) {
        this.actName = actName;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getProcInstId() {
        return procInstId;
    }

    public void setProcInstId(String procInstId) {
        this.procInstId = procInstId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public List<KeyValue> getProcess() {
        return process;
    }

    public void setProcess(List<KeyValue> process) {
        this.process = process;
    }
}