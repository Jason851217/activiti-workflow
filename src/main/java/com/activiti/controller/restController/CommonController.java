package com.activiti.controller.restController;

import com.activiti.common.aop.ApiAnnotation;
import com.activiti.mapper.ToolsMapper;
import com.activiti.pojo.schedule.ScheduleDto;
import com.activiti.pojo.tools.InvokeLog;
import com.activiti.service.CommonService;
import com.activiti.service.ScheduleService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeComparator;
import org.joda.time.DateTimeFieldType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;


/**
 * Created by 12490 on 2017/8/14.
 */
@RestController
@RequestMapping("/api/common")
public class CommonController {
    @Autowired
    private CommonService commonService;
    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private ToolsMapper toolsMapper;

    /**
     * GitHub请求题目和答案
     *
     * @param qDir
     * @param qNo
     * @return
     */
    @RequestMapping("/getQAContent")
    @ResponseBody
    @ApiAnnotation
    public Object getQAFromGitHub(@RequestParam(value = "qDir", required = true) String qDir,
                                  @RequestParam(value = "qNo", required = true) String qNo) {
        return commonService.getQAFromGitHub(qDir, qNo);
    }

    /**
     * 查询指定课程的互评时间表
     *
     * @param courseCode
     * @return
     */
    @RequestMapping("/selectScheduleTime")
    @ResponseBody
    @ApiAnnotation
    public Object selectScheduleTime(@RequestParam(value = "courseCode", required = true) String courseCode) {
        return scheduleService.selectScheduleTime(courseCode);
    }

    /**
     * 插入指定课程的互评时间
     *
     * @param param
     * @return
     */
    @RequestMapping("/insertScheduleTime")
    @ResponseBody
    @ApiAnnotation
    public Object insertScheduleTime(@RequestParam(required = true,value = "data") String param) throws Exception {
        JSONObject jsonObject= JSON.parseObject(param);
        ScheduleDto scheduleDto=jsonObject.toJavaObject(ScheduleDto.class);
        String courseCode = scheduleDto.getCourseCode();
        if (null!=scheduleService.selectScheduleTime(courseCode))throw new Exception(courseCode+"该课程已经存在");
        if (null == courseCode) throw new Exception("courseCode字段不能为空");
        scheduleService.insertScheduleTime(scheduleDto);
        return "课程部署成功";
    }

    /**
     * 更新指定课程的互评时间
     *
     * @param scheduleDto
     * @return
     */
    @RequestMapping("/updateScheduleTime")
    @ResponseBody
    @ApiAnnotation
    public Object updateScheduleTime(ScheduleDto scheduleDto) throws Exception {
        String courseCode = scheduleDto.getCourseCode();
        if (null == courseCode) throw new Exception("courseCode字段不能为空");
        scheduleService.updateScheduleTime(scheduleDto);
        return scheduleService.selectScheduleTime(courseCode);
    }

    /**
     * 查询所有课程相关的时间表
     *
     * @return
     */
    @RequestMapping("/selectAllScheduleTime")
    @ResponseBody
    @ApiAnnotation
    public Object selectAllScheduleTime() {
        return scheduleService.selectAllScheduleTime();
    }

    /**
     * 查询指定课程所处的阶段
     *
     * @return -1:该课程没有互评流程   0：还没开始提交作业  1：提交作业阶段  2：互评分配阶段,等待互评  3：互评阶段
     * 4：等待审核阶段  5：审核阶段  6：等待公布成绩  7：公布成绩阶段
     */
    @RequestMapping("/selectTimeStage")
    @ResponseBody
    @ApiAnnotation
    public Object selectAllScheduleTime(@RequestParam(value = "courseCode", required = true) String courseCode) {
        ScheduleDto scheduleDto = scheduleService.selectScheduleTime(courseCode);
        if (null == scheduleDto) return -1;
        DateTime nowDate = new DateTime();
        DateTimeComparator comparator = DateTimeComparator.getInstance(DateTimeFieldType.secondOfDay());
        if (comparator.compare(nowDate, new DateTime(scheduleDto.getStartTime())) < 0)
            return 0;
        if (comparator.compare(nowDate, new DateTime(scheduleDto.getStartTime())) > 0 &&
                comparator.compare(nowDate, new DateTime(scheduleDto.getCommitEndTime())) < 0)
            return 1;
        if (comparator.compare(nowDate, new DateTime(scheduleDto.getCommitEndTime())) > 0 &&
                comparator.compare(nowDate, new DateTime(scheduleDto.getJudgeStartTime())) < 0)
            return 2;
        if (comparator.compare(nowDate, new DateTime(scheduleDto.getJudgeStartTime())) > 0 &&
                comparator.compare(nowDate, new DateTime(scheduleDto.getJudgeEndTime())) < 0)
            return 3;
        if (comparator.compare(nowDate, new DateTime(scheduleDto.getJudgeEndTime())) > 0 &&
                comparator.compare(nowDate, new DateTime(scheduleDto.getAuditStartTime())) < 0)
            return 4;
        if (comparator.compare(nowDate, new DateTime(scheduleDto.getAuditStartTime())) > 0 &&
                comparator.compare(nowDate, new DateTime(scheduleDto.getAuditEndTime())) < 0)
            return 5;
        if (comparator.compare(nowDate, new DateTime(scheduleDto.getAuditEndTime())) > 0 &&
                comparator.compare(nowDate, new DateTime(scheduleDto.getPublishTime())) < 0)
            return 6;
        if (comparator.compare(nowDate, new DateTime(scheduleDto.getPublishTime())) > 0)
            return 7;
        return -1;
    }

    /**
     * 日志查询
     *
     * @param page
     * @param limit
     * @return
     */
    @ResponseBody
    @RequestMapping("/selectInvokeLog")
    @ApiAnnotation(insertLog = false)
    public Object selectInvokeLog(@RequestParam(value = "page", required = true) long page,
                                  @RequestParam(value = "limit", required = true) int limit) {
        return  toolsMapper.selectInvokeLog((page - 1) * limit, limit);
    }

    /**
     * 查询总页数
     * @return
     */
    @ResponseBody
    @RequestMapping("/countInvokeLog")
    @ApiAnnotation(insertLog = false)
    public Object countInvokeLog() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("count", toolsMapper.countInvokeLog());
        return  jsonObject;
    }
}
