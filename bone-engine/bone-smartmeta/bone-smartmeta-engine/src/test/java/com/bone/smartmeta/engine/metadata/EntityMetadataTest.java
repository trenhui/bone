package com.bone.smartmeta.engine.metadata;

import com.bone.smartmeta.engine.model.FieldMetadata;
import com.bone.smartmeta.engine.model.FieldType;
import com.bone.smartmeta.engine.model.ValidationRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EntityMetadataTest {

    private EntityMetadata customerMetadata;
    private FieldMetadata idField;
    private FieldMetadata nameField;
    private FieldMetadata ageField;
    private FieldMetadata emailField;

    @BeforeEach
    void setUp() {
        // 初始化实体元数据
        customerMetadata = new EntityMetadata();
        customerMetadata.setEntityName("Customer");
        customerMetadata.setDescription("客户实体");
        customerMetadata.setNamespace("crm");
        
        // 初始化字段元数据
        initFields();
        
        // 添加字段到实体
        List<FieldMetadata> fields = Arrays.asList(idField, nameField, ageField, emailField);
        customerMetadata.setFields(fields);
        
        // 设置主键字段
        customerMetadata.setPrimaryKeyField("id");
    }

    private void initFields() {
        // ID字段
        idField = new FieldMetadata();
        idField.setFieldName("id");
        idField.setFieldType(FieldType.STRING);
        idField.setDescription("客户ID");
        idField.setRequired(true);
        idField.setUnique(true);
        
        // 名称字段
        nameField = new FieldMetadata();
        nameField.setFieldName("name");
        nameField.setFieldType(FieldType.STRING);
        nameField.setDescription("客户姓名");
        nameField.setRequired(true);
        nameField.setMinLength(2);
        nameField.setMaxLength(50);
        
        // 年龄字段
        ageField = new FieldMetadata();
        ageField.setFieldName("age");
        ageField.setFieldType(FieldType.INTEGER);
        ageField.setDescription("客户年龄");
        ageField.setRequired(true);
        ageField.setMinValue(18);
        ageField.setMaxValue(120);
        
        // 添加验证规则
        ValidationRule ageValidationRule = new ValidationRule();
        ageValidationRule.setRuleName("AdultValidation");
        ageValidationRule.setExpression("value >= 18");
        ageValidationRule.setErrorMessage("年龄必须满18岁");
        ageField.setValidationRules(Arrays.asList(ageValidationRule));
        
        // 邮箱字段
        emailField = new FieldMetadata();
        emailField.setFieldName("email");
        emailField.setFieldType(FieldType.STRING);
        emailField.setDescription("客户邮箱");
        emailField.setRequired(true);
        emailField.setPattern("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");
        
        // 添加计算表达式
        emailField.setCalculated(false);
    }

    @Test
    void testEntityMetadataBasics() {
        // 验证实体元数据基本属性
        assertEquals("Customer", customerMetadata.getEntityName());
        assertEquals("客户实体", customerMetadata.getDescription());
        assertEquals("crm", customerMetadata.getNamespace());
        assertEquals("id", customerMetadata.getPrimaryKeyField());
    }

    @Test
    void testGetFieldByName() {
        // 测试获取字段
        FieldMetadata retrievedIdField = customerMetadata.getFieldByName("id");
        FieldMetadata retrievedNameField = customerMetadata.getFieldByName("name");
        FieldMetadata nonExistentField = customerMetadata.getFieldByName("nonExistent");
        
        // 验证结果
        assertNotNull(retrievedIdField);
        assertEquals("id", retrievedIdField.getFieldName());
        
        assertNotNull(retrievedNameField);
        assertEquals("name", retrievedNameField.getFieldName());
        
        assertNull(nonExistentField);
    }

    @Test
    void testGetRequiredFields() {
        // 获取必填字段
        List<FieldMetadata> requiredFields = customerMetadata.getRequiredFields();
        
        // 验证结果
        assertNotNull(requiredFields);
        assertEquals(4, requiredFields.size()); // 所有字段都是必填的
        
        // 检查每个字段都是必填的
        for (FieldMetadata field : requiredFields) {
            assertTrue(field.isRequired());
        }
    }

    @Test
    void testGetUniqueFields() {
        // 获取唯一字段
        List<FieldMetadata> uniqueFields = customerMetadata.getUniqueFields();
        
        // 验证结果
        assertNotNull(uniqueFields);
        assertEquals(1, uniqueFields.size()); // 只有id字段是唯一的
        assertEquals("id", uniqueFields.get(0).getFieldName());
    }

    @Test
    void testGetCalculatedFields() {
        // 获取计算字段（当前没有设置计算字段）
        List<FieldMetadata> calculatedFields = customerMetadata.getCalculatedFields();
        
        // 验证结果
        assertNotNull(calculatedFields);
        assertTrue(calculatedFields.isEmpty());
        
        // 设置一个计算字段并重新测试
        FieldMetadata calculatedField = new FieldMetadata();
        calculatedField.setFieldName("displayName");
        calculatedField.setFieldType(FieldType.STRING);
        calculatedField.setCalculated(true);
        calculatedField.setCalculationExpression("name + '(' + age + '岁)'");
        
        List<FieldMetadata> updatedFields = new ArrayList<>(customerMetadata.getFields());
        updatedFields.add(calculatedField);
        customerMetadata.setFields(updatedFields);
        
        calculatedFields = customerMetadata.getCalculatedFields();
        assertNotNull(calculatedFields);
        assertEquals(1, calculatedFields.size());
        assertEquals("displayName", calculatedFields.get(0).getFieldName());
    }

    @Test
    void testValidateMetadata_Success() {
        // 验证有效的元数据
        List<String> errors = customerMetadata.validateMetadata();
        
        // 应该没有错误
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void testValidateMetadata_MissingEntityName() {
        // 创建一个缺少实体名称的元数据
        EntityMetadata invalidMetadata = new EntityMetadata();
        invalidMetadata.setDescription("无效实体");
        
        // 验证元数据
        List<String> errors = invalidMetadata.validateMetadata();
        
        // 应该有错误
        assertNotNull(errors);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.contains("实体名称不能为空")));
    }

    @Test
    void testValidateMetadata_MissingPrimaryKey() {
        // 创建一个缺少主键的元数据
        EntityMetadata invalidMetadata = new EntityMetadata();
        invalidMetadata.setEntityName("InvalidEntity");
        invalidMetadata.setFields(Arrays.asList(nameField)); // 只有name字段
        
        // 验证元数据
        List<String> errors = invalidMetadata.validateMetadata();
        
        // 应该有错误
        assertNotNull(errors);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.contains("主键字段不存在")));
    }

    @Test
    void testValidateMetadata_DuplicateFieldNames() {
        // 创建一个有重复字段名称的元数据
        EntityMetadata invalidMetadata = new EntityMetadata();
        invalidMetadata.setEntityName("InvalidEntity");
        invalidMetadata.setPrimaryKeyField("id");
        
        // 创建重复名称的字段
        FieldMetadata duplicateField = new FieldMetadata();
        duplicateField.setFieldName("name"); // 与nameField重名
        duplicateField.setFieldType(FieldType.STRING);
        
        // 添加字段
        List<FieldMetadata> duplicateFields = Arrays.asList(idField, nameField, duplicateField);
        invalidMetadata.setFields(duplicateFields);
        
        // 验证元数据
        List<String> errors = invalidMetadata.validateMetadata();
        
        // 应该有错误
        assertNotNull(errors);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.contains("字段名称重复")));
    }

    @Test
    void testFieldTypeValidation() {
        // 验证字段类型
        assertEquals(FieldType.STRING, customerMetadata.getFieldByName("id").getFieldType());
        assertEquals(FieldType.INTEGER, customerMetadata.getFieldByName("age").getFieldType());
        assertEquals(FieldType.STRING, customerMetadata.getFieldByName("email").getFieldType());
    }

    @Test
    void testFieldValidationRules() {
        // 获取age字段的验证规则
        FieldMetadata ageField = customerMetadata.getFieldByName("age");
        List<ValidationRule> validationRules = ageField.getValidationRules();
        
        // 验证规则
        assertNotNull(validationRules);
        assertEquals(1, validationRules.size());
        assertEquals("AdultValidation", validationRules.get(0).getRuleName());
        assertEquals("value >= 18", validationRules.get(0).getExpression());
        assertEquals("年龄必须满18岁", validationRules.get(0).getErrorMessage());
    }

    @Test
    void testFieldConstraints() {
        // 验证字段约束
        FieldMetadata nameField = customerMetadata.getFieldByName("name");
        assertEquals(2, nameField.getMinLength());
        assertEquals(50, nameField.getMaxLength());
        
        FieldMetadata ageField = customerMetadata.getFieldByName("age");
        assertEquals(18, ageField.getMinValue());
        assertEquals(120, ageField.getMaxValue());
        
        FieldMetadata emailField = customerMetadata.getFieldByName("email");
        assertEquals("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$", emailField.getPattern());
    }

    @Test
    void testIsValidFieldName() {
        // 测试有效字段名
        assertTrue(customerMetadata.isValidFieldName("id"));
        assertTrue(customerMetadata.isValidFieldName("name"));
        assertTrue(customerMetadata.isValidFieldName("age"));
        assertTrue(customerMetadata.isValidFieldName("email"));
        
        // 测试无效字段名
        assertFalse(customerMetadata.isValidFieldName("nonExistent"));
        assertFalse(customerMetadata.isValidFieldName(null));
        assertFalse(customerMetadata.isValidFieldName(""));
    }

    @Test
    void testGetFieldNames() {
        // 获取所有字段名称
        List<String> fieldNames = customerMetadata.getFieldNames();
        
        // 验证结果
        assertNotNull(fieldNames);
        assertEquals(4, fieldNames.size());
        assertTrue(fieldNames.contains("id"));
        assertTrue(fieldNames.contains("name"));
        assertTrue(fieldNames.contains("age"));
        assertTrue(fieldNames.contains("email"));
    }

    @Test
    void testClone() {
        // 克隆实体元数据
        EntityMetadata clonedMetadata = customerMetadata.clone();
        
        // 验证克隆结果
        assertEquals(customerMetadata.getEntityName(), clonedMetadata.getEntityName());
        assertEquals(customerMetadata.getDescription(), clonedMetadata.getDescription());
        assertEquals(customerMetadata.getNamespace(), clonedMetadata.getNamespace());
        assertEquals(customerMetadata.getPrimaryKeyField(), clonedMetadata.getPrimaryKeyField());
        
        // 验证字段数量
        assertEquals(customerMetadata.getFields().size(), clonedMetadata.getFields().size());
        
        // 修改克隆体不影响原对象
        clonedMetadata.setDescription("修改后的描述");
        assertNotEquals(customerMetadata.getDescription(), clonedMetadata.getDescription());
    }

    @Test
    void testEqualsAndHashCode() {
        // 创建相同的实体元数据
        EntityMetadata sameMetadata = new EntityMetadata();
        sameMetadata.setEntityName("Customer");
        sameMetadata.setNamespace("crm");
        sameMetadata.setFields(Arrays.asList(idField, nameField, ageField, emailField));
        sameMetadata.setPrimaryKeyField("id");
        
        // 创建不同的实体元数据
        EntityMetadata differentMetadata = new EntityMetadata();
        differentMetadata.setEntityName("DifferentEntity");
        differentMetadata.setNamespace("crm");
        
        // 验证相等性
        assertEquals(customerMetadata, sameMetadata);
        assertEquals(customerMetadata.hashCode(), sameMetadata.hashCode());
        assertNotEquals(customerMetadata, differentMetadata);
        assertNotEquals(customerMetadata.hashCode(), differentMetadata.hashCode());
        assertNotEquals(customerMetadata, null);
        assertNotEquals(customerMetadata, "not a metadata");
    }
}