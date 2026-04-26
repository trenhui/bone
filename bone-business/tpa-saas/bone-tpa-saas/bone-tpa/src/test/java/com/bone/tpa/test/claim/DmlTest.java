package com.bone.tpa.test.claim;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.bone.metadata.sdk.enums.FieldType;
import com.bone.metadata.sdk.repository.DBConstants;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SnowUtil;
import com.bone.tpa.claim.application.dto.ClaimDTO;
import com.bone.tpa.claim.application.dto.ClaimInvoiceDTO;
import com.bone.tpa.claim.application.dto.InvoiceProjectItemDTO;
import com.bone.tpa.claim.application.dto.SignRecordDTO;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.response.BenefitPerson;
import com.bone.tpa.claim.application.response.ClaimDetailObject;
import com.bone.tpa.claim.application.response.CollectBusiness;
import com.bone.tpa.claim.application.response.CollectPerson;
import com.bone.tpa.config.JacksonConfig;
import com.bone.tpa.intelligent.adjustment.dto.PolicyDTO;
import com.bone.tpa.intelligent.adjustment.model.AdjustConclusion;
import com.bone.tpa.intelligent.adjustment.model.AdjustResult;
import com.bone.tpa.intelligent.adjustment.model.AdjustmentInfo;
import com.bone.tpa.intelligent.adjustment.model.PolicyInfoModel;
import com.bone.tpa.sdk.adjustment.model.liability.AdjustmentDetail;
import com.bone.tpa.sdk.adjustment.response.InvoiceAdjustmentResponse;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.test.BaseTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Transient;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DmlTest extends BaseTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private JacksonConfig config;

    private String bizModelTemplate = "insert into meta_biz_model (table_name, code, name, field_code, field_name, " +
            "component_type, extra_field_prefix, data_binding_prefix, status, deleted, create_time, update_time, create_by, update_by) values " +
            "('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', 1, 0, now(), now(), 0, 0);";


    private List<String> tableNameList = List.of("ia_adjustment_record", "ia_adjustment_result",
            "ia_coverage", "ia_liability", "ia_liability_sharing", "ia_liability_sharing_relation", "ia_plan",
            "ia_policy", "ia_personal_quota", "ia_personal_quota_change", "ia_personal_quota_change_batch",

            "ss_claim", "ss_claim_hint_msg", "ss_claim_image", "ss_claim_invoice", "ss_claim_stakeholder", "ss_claim_track_log",
            "ss_file_upload_record", "ss_invoice_image_relation", "ss_invoice_project", "ss_invoice_project_item", "ss_sign_record", "ss_sign_record_track_log");

    @Test
    public void test() {
        Assert.notNull(jdbcTemplate, "metaTableRespository null");
    }

    @Test
    public void batchDdl() {
        for (String tableName : tableNameList) {
            testDdl(tableName);
        }
    }


    @Test
    public void testDdl(String tableName){
        String appCode = "tpa";
        String schema = "tpa-saas";
//        String tableName = "ss_sign_record";
        String sql = "SELECT COLUMN_NAME, COLUMN_COMMENT FROM INFORMATION_SCHEMA.COLUMNS \n" +
                "WHERE TABLE_SCHEMA ='"+schema+"' and TABLE_NAME='"+tableName+"'";
        List<Map<String, Object>>    list =  jdbcTemplate.queryForList(sql,new HashMap<>());
        System.out.println("#" + list);
        Map<String,String> columnCommentMap = new HashMap<>();
        for(Map<String, Object> mp : list){
            columnCommentMap.put((String)mp.get("COLUMN_NAME"),(String)mp.get("COLUMN_COMMENT"));
        }


        List<Map<String, Object>>  collList =  jdbcTemplate.queryForList("desc" +DBConstants.SPACE+tableName+ DBConstants.SPACE,new HashMap<>());
        String tableCommentSq =
                String.format( "SELECT TABLE_COMMENT \n" +
                        "FROM INFORMATION_SCHEMA.TABLES \n" +
                        "WHERE TABLE_SCHEMA = '%s' \n" +
                        "  AND TABLE_NAME = '%s';",schema,tableName);
        String tableComment =  jdbcTemplate.queryForObject(tableCommentSq,new HashMap<>(),String.class);

        List<String> systemFieldList = PkListUtil.asList("id","create_time",
                "update_time","deleted","update_by","create_by","app_code",
                "biz_identity_code");

        StringBuffer sb = new StringBuffer();
        sb.append(String.format("delete from meta_table where table_name= '%s' and app_code = '%s';\n ",tableName,appCode));
        Long tableId = IdUtil.getSnowflake(SnowUtil.getWorkId(),1).nextId();

        sb.append(String.format("insert into   meta_table (id,deleted,create_time,update_time,create_by,update_by,app_code,table_name,table_remark)" +
                "values (%s,false,now(),now(),0,0,'%s','%s','%s');\n ",tableId,appCode,tableName,tableComment));

        sb.append(String.format("delete from meta_table_field where table_name = '%s' and app_code = '%s'; \n",tableName,appCode));
        for(Map<String,Object> mp : collList){
            String columnName = (String)mp.get("Field");
            String type = (String)mp.get("Type");
            String nullAble = (String)mp.get("Null");
            String defaultVal = (String) mp.get("Default");
            Long id = IdUtil.getSnowflake(1,1).nextId();
            String appCodeLocal = appCode;
            String tableNameLocal = tableName;
            String fieldType = "";
            if(type.startsWith("bigint") ||
                    type.startsWith("tinyint")
                    || type.startsWith("int")
                    ||type.startsWith("smallint")
                    || type.startsWith("tinyint")){
                fieldType = FieldType.INT.getCode();
            }else if(type.contains("varchar")
                    || type.contains("char")
            ){
                fieldType = FieldType.VARCAHR.getCode();

            }else if(   type.contains("text")
            ){
                fieldType = FieldType.TEXT.getCode();

            }else if(   type.contains("date") || type.contains("time")
            ){
                fieldType = FieldType.DATE_TIME.getCode();

            }else if(   type.contains("bool")
            ){
                fieldType = FieldType.BOOLEAN.getCode();

            }else if(   type.contains("decimal")
                    || type.contains("float")
                    ||type.contains("double")
            ){
                fieldType = FieldType.DECIMAL.getCode();

            }else{
                throw new RuntimeException("not support type : "+type);
            }
            String fieldRemark =columnCommentMap.get(columnName);
            if(fieldRemark==null){
                fieldRemark = "";
            }

            Integer fieldLength = getLength(type);
            Boolean fieldNullable = StringUtils.equals("NO",nullAble)?false:true;
            Boolean systemField = systemFieldList.contains(columnName);
            String fieldTemplate = "insert into meta_table_field " +
                    "(id,create_time,update_time,update_by,create_by," +
                    "deleted,app_code,table_name,field_name,field_type," +
                    "field_remark,field_length,field_nullable,system_field)" +
                    "values (%s,now(),now(),0,0," +
                    "false,'%s','%s','%s','%s'," +
                    "'%s',%s,%s,%s); \n ";

            String fieldSql =  String.format(fieldTemplate,
                    id,appCodeLocal,tableNameLocal,columnName,fieldType,fieldRemark,
                    fieldLength,
                    fieldNullable,systemField);
            sb.append(fieldSql);
        }

        System.out.println(sb.toString());
    }

    private Integer getLength(String type){
        if(!StringUtils.contains(type,"(")){
            return null;
        }
        String str = StringUtils.substringAfter(type,"(");
        str = StringUtils.substringBefore(str,")");
        if(!StringUtils.contains(str,",")){
            return  Integer.valueOf(str);
        }
        return null;

    }

    private Boolean checkFieldType(Class<?> clazz) {
        if (clazz.isPrimitive()) return true;
        if (Number.class.isAssignableFrom(clazz)) return true;
        return clazz == String.class ||
                clazz == Date.class ||
                clazz == Integer.class ||
                clazz == Long.class ||
                clazz == Short.class ||
                clazz == Byte.class ||
                clazz == Character.class ||
                clazz == Float.class ||
                clazz == Double.class ||
                clazz == Boolean.class;
    }

    /**
     * 转化成前端展示使用的组件类型
     * Input：文本单行输入框，SelectDrop：下拉框，DateTime：日期时间，DateRange：日期区间，SelectCtrl：级联下拉框，InputNum：数字单行输入框
     */
    private String transferFieldType(Field field) {
        //对于下拉框基本只能用注释来处理
        if (field.getAnnotation(Schema.class) != null) {
            if (!field.getAnnotation(Schema.class).format().isEmpty()) {
                return field.getAnnotation(Schema.class).format();
            }
        }

        if (Number.class.isAssignableFrom(field.getType())) return "InputNum";

        if (field.getType().equals(BigDecimal.class)) return "InputNum";

        //如果字段的结尾是time或者date，应当是日期时间
        if (field.getName().endsWith("Time") || field.getName().endsWith("Date") || field.getName().endsWith("Birthday")
                || field.getName().endsWith("Day")) {
            return "DateTime";
        }
        //如果字段的结尾是Period，应当是时间区间
        if (field.getName().endsWith("Period")) {
            return "DateRange";
        }

        //最终返回单行输入应该是没问题
        return "Input";
    }

    private void generateMetaBizModel(String databindingPrefix, Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();

        String code = null;
        String name = null;
        BizModelEnum bizFieldEnum;
        if (clazz.getAnnotation(Schema.class) != null) {
            code = clazz.getAnnotation(Schema.class).name();
            name = clazz.getAnnotation(Schema.class).description();
            //System.out.println("delete from meta_biz_model where code = '" + code + "';");
            bizFieldEnum = BizModelEnum.getByCode(code);

            if (!clazz.getAnnotation(Schema.class).type().equals("noTime")) {
                System.out.println(String.format(bizModelTemplate, bizFieldEnum.getTableName(), code, name, "createTime",
                        "创建时间", "DateTime", bizFieldEnum.getPrefix(), databindingPrefix));

                System.out.println(String.format(bizModelTemplate, bizFieldEnum.getTableName(), code, name, "updateTime",
                        "修改时间", "DateTime", bizFieldEnum.getPrefix(), databindingPrefix));
            }
        } else {
            return;
        }

        for (Field field : fields) {
            //如果是基础类或StringDate这些
            if (checkFieldType(field.getType())) {
                String fieldCode = field.getName();
                //使用Transient这个关键字来手动标注不需要生成模型的字段
                //后面看看有没有更好的办法
                if (field.getAnnotation(Transient.class) != null) {
                    continue;
                }

                //如果没打注释就不做了
                if (field.getAnnotation(Schema.class) == null) {
                    continue;
                }

                String fieldName = field.getAnnotation(Schema.class).description();

                String sql = String.format(bizModelTemplate, bizFieldEnum.getTableName(), code, name, fieldCode,
                        fieldName, transferFieldType(field), bizFieldEnum.getPrefix(), databindingPrefix);

                System.out.println(sql);
            } else if (field.getType() == Map.class) {
                //如果是map, 那就是扩展字段，跳过
            } else if (field.getType() == List.class){
                //如果是List, 则要解析其中的字段
                Type genericFieldType = field.getGenericType();

                if (genericFieldType instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) genericFieldType;
                    Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

                    if (actualTypeArguments.length > 0 && actualTypeArguments[0] instanceof Class) {
                        Class<?> genericClass = (Class<?>) actualTypeArguments[0];
                        generateMetaBizModel(databindingPrefix + field.getName() + ".", genericClass);
                    }
                }
            } else {
                //如果均不是，则递归调用本函数
                generateMetaBizModel(databindingPrefix + field.getName() + ".", field.getType());
            }
        }
    }



    private List<Class<?>> bizmodelList = List.of(ClaimDetailObject.class, ClaimInvoiceDTO.class, InvoiceProjectItemDTO.class,
            BenefitPerson.class, CollectPerson.class, CollectBusiness.class, PolicyInfoModel.class, PolicyDTO.class, InvoiceAdjustmentResponse.class);

    private List<Class<?>> subModelList = List.of(ClaimDTO.class);

    @Test
    public void batchMetaBizModel() {
        for (Class<?> clazz : bizmodelList) {
            metaBizModelTest(clazz);
        }
    }


    @Test
    public void metaBizModelTest(Class<?> clazz){
//        Class<?> clazz = InvoiceProjectItemDTO.class;
        generateMetaBizModel("$.", clazz);
    }


    @Test
    public void minorTest() throws JsonProcessingException {
        String json1 = "{\"visitDate\":\"2025\", \"relatedId\":\"12345678901234\"}";
        String json2 = "{\"visitDate\":\"2025-06\", \"relatedId\":\"12345678901234\"}";
        String json3 = "{\"visitDate\":\"2025-06-18\", \"relatedId\":\"12345678901234\"}";
        String json4 = "{\"visitDate\":\"2025-06-18 05\", \"relatedId\":\"12345678901234\"}";
        String json5 = "{\"visitDate\":\"2025-06-18 05:05\", \"relatedId\":\"12345678901234\"}";
        String json6 = "{\"visitDate\":\"2025-06-18 05:05:05\", \"relatedId\":\"12345678901234\"}";

        ObjectMapper mapper = config.jacksonObjectMapper(new Jackson2ObjectMapperBuilder());

        ClaimInvoice obj1 = mapper.readValue(json1, ClaimInvoice.class);
        System.out.println(obj1.getVisitDate());  // 应正确输出日期

        ClaimInvoice obj2 = mapper.readValue(json2, ClaimInvoice.class);
        System.out.println(obj2.getVisitDate());  // 应正确输出日期

        ClaimInvoice obj3 = mapper.readValue(json3, ClaimInvoice.class);
        System.out.println(obj3.getVisitDate());  // 应正确输出日期

        ClaimInvoice obj4 = mapper.readValue(json4, ClaimInvoice.class);
        System.out.println(obj4.getVisitDate());  // 应正确输出日期

        ClaimInvoice obj5 = mapper.readValue(json5, ClaimInvoice.class);
        System.out.println(obj5.getVisitDate());  // 应正确输出日期

        ClaimInvoice obj6 = mapper.readValue(json6, ClaimInvoice.class);
        System.out.println(obj6.getVisitDate());  // 应正确输出日期
    }
}
