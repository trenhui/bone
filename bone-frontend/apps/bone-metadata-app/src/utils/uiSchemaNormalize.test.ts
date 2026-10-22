import { describe, it, expect } from 'vitest';
import { normalizeUiSchema, validateUiSchema, type BoneUiSchema } from '@bone/ui-schema';

// 复刻 claim-app 形态：views.form.sections 用分区 code 引用顶部 sections 池
const codeRefSchema = {
  schemaVersion: 1,
  namespace: 'test',
  i18n: { namespace: 'test', fallbackLocale: 'zh-CN' },
  sections: [
    { sectionCode: 's1', title: '基本信息', controls: [{ controlCode: 'c1', fieldCode: 'name', controlKind: 'TEXT' }] },
  ],
  views: { form: { sections: ['s1'] } },
  actionBar: [{ actionCode: 'confirm', sortOrder: 1 }],
} as unknown as BoneUiSchema;

// 规范形态：内联分区对象
const inlineSchema = {
  schemaVersion: 1,
  namespace: 'test',
  i18n: { namespace: 'test', fallbackLocale: 'zh-CN' },
  views: {
    form: {
      sections: [{ id: 's1', title: '基本信息', controls: [{ controlCode: 'c1', fieldCode: 'name', controlKind: 'TEXT' }] }],
    },
  },
  actionBar: [{ actionCode: 'confirm', sortOrder: 1 }],
} as unknown as BoneUiSchema;

describe('normalizeUiSchema', () => {
  it('把分区 code 引用解析为内联分区对象（规范形态）', () => {
    const { schema, errors } = normalizeUiSchema(codeRefSchema);
    expect(errors).toHaveLength(0);
    const sec = schema.views.form!.sections[0] as { sectionCode?: string };
    expect(sec.sectionCode).toBe('s1');
    expect((sec as { controls: unknown[] }).controls).toHaveLength(1);
  });

  it('内联形态原样透传、不产生错误', () => {
    const { schema, errors } = normalizeUiSchema(inlineSchema);
    expect(errors).toHaveLength(0);
    expect(schema.views.form!.sections).toHaveLength(1);
  });

  it('无法解析的 code 引用进入 errors 且不抛异常', () => {
    const bad = { ...codeRefSchema, views: { form: { sections: ['nope'] } } } as unknown as BoneUiSchema;
    const { schema, errors } = normalizeUiSchema(bad);
    expect(errors.length).toBeGreaterThan(0);
    expect(schema.views.form!.sections).toHaveLength(0);
  });
});

describe('validateUiSchema', () => {
  it('规范 schema 通过校验', () => {
    expect(validateUiSchema(inlineSchema).valid).toBe(true);
  });

  it('分区 code 引用可解析时通过校验', () => {
    expect(validateUiSchema(codeRefSchema).valid).toBe(true);
  });

  it('缺少必备字段报错', () => {
    const r = validateUiSchema({} as unknown as BoneUiSchema);
    expect(r.valid).toBe(false);
    expect(r.errors.join(' ')).toMatch(/schemaVersion|namespace/);
  });

  it('控件缺 fieldCode/controlCode 报错', () => {
    const bad = {
      schemaVersion: 1,
      namespace: 't',
      views: { form: { sections: [{ id: 's1', controls: [{ controlKind: 'TEXT' }] }] } },
    } as unknown as BoneUiSchema;
    const r = validateUiSchema(bad);
    expect(r.valid).toBe(false);
    expect(r.errors.join(' ')).toMatch(/fieldCode\/controlCode/);
  });

  it('actionBar 重复 actionCode 报错', () => {
    const bad = {
      ...inlineSchema,
      actionBar: [{ actionCode: 'confirm' }, { actionCode: 'confirm' }],
    } as unknown as BoneUiSchema;
    const r = validateUiSchema(bad);
    expect(r.valid).toBe(false);
    expect(r.errors.join(' ')).toMatch(/重复 actionCode/);
  });
});
