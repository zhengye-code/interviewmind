package com.interviewmind.modules.interview.service;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import com.interviewmind.modules.interview.model.SkillDefinition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class InterviewSkillService {
    private static final String SKILL_GLOB = "classpath*:skills/*/SKILL.md";
    private static final Pattern FRONT_MATTER_PATTERN =
            Pattern.compile("(?s)^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n?(.*)$");
    private static final Pattern SKILL_ID_PATTERN =
            Pattern.compile(".*/skills/([^/]+)/SKILL\\.md$");
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private final Yaml yaml = new Yaml();
    private final Map<String, SkillDefinition> registry = new TreeMap<>();
    private final Map<String, String> skillBodies = new HashMap<>();
    @PostConstruct
    void loadSkills(){
        List<String> loadedIds=new ArrayList<>();
        try {
            Resource[] resources=resourceResolver.getResources(SKILL_GLOB);
            for (Resource resource : resources) {
                String skillId=extractSkillId(resource);
                if(skillId==null){
                    log.warn("无法从资源路径解析出 skillId，跳过: {}", resource.getDescription());
                    continue;
                }
                loadOne(skillId,resource);
                loadedIds.add(skillId);
            }
        } catch (IOException e) {
            throw new IllegalStateException("扫描面试skill失败",e);
        }
        if(registry.isEmpty()){
            throw new IllegalStateException("没有加载到任何面试 Skill，请检查 " + SKILL_GLOB);
        }
        log.info("面试 Skill 加载完成，共 {} 个: {}", registry.size(), loadedIds);
    }
    private void loadOne(String skillId,Resource resource)throws IOException{
        String markdown=resource.getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(markdown);
        if(!matcher.matches()){
            throw new IllegalStateException("SKILL.md缺少front matter:"+resource.getDescription());
        }
        SkillDefinition skillDefinition = yaml.loadAs(matcher.group(1), SkillDefinition.class);
        if(skillDefinition==null||skillDefinition.getName()==null){
            throw new IllegalStateException("SKILL.md front matter解析为空:"+resource.getDescription());
        }
        skillDefinition.setId(skillId);
        mergeData(skillId,skillDefinition);
        registry.put(skillId,skillDefinition);
        skillBodies.put(skillId,matcher.group(2).trim());
        log.info("已加载 Skill: id={}, name={}, displayName={}",
                skillId, skillDefinition.getName(), skillDefinition.getDisplayName());

    }
    private String extractSkillId(Resource resource){
        try {
            Matcher matcher = SKILL_ID_PATTERN.matcher(resource.getURL().toString());
            return  matcher.find() ? matcher.group(1) : null;
        }catch (Exception e){
            return null;
        }
    }
    private void mergeData(String skillId, SkillDefinition skillDefinition){
        String metaPath = "classpath:skills/" + skillId + "/skill.meta.yml";
        Resource metaResource = resourceResolver.getResource(metaPath);
        if(!metaResource.exists()){
            log.warn("SKILL {} 没有skill.meta.yaml，使用默认展示信息",skillId);
            return;
        }
        try (InputStream in=metaResource.getInputStream()){
            SkillDefinition metaDefinition = yaml.loadAs(in, SkillDefinition.class);
            if(metaDefinition!=null){
                skillDefinition.setDisplayName(metaDefinition.getDisplayName());
                skillDefinition.setDisplay(metaDefinition.getDisplay());
                if(metaDefinition.getCategories()!=null){
                    skillDefinition.setCategories(metaDefinition.getCategories());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取skill.meta.yaml失败："+metaPath,e);
        }
    }
    public  SkillDefinition getSkillDefinition(String skillId){
        SkillDefinition skillDefinition = registry.get(skillId);
        if(skillDefinition==null){
            throw new BusinessException(ErrorCode.INTERVIEW_SKILL_NOT_FOUND,"面试方向不存在："+skillId);
        }
        return skillDefinition;
    }
    public List<SkillDefinition> getSkillDefinitions(){
        return List.copyOf(registry.values());
    }
    public String getSkillBodies(String skillId){
        return skillBodies.getOrDefault(skillId,"");
    }
}
