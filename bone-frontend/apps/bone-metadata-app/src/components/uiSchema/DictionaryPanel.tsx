/**
 * 字典覆写面板：只允许改显示名 / 隐藏选项，禁止新造选项。
 *
 * 协议 §3.1 明文：枚举/码表的真源在元数据域（meta_field.enumValues），UI Schema 只"引用"。
 * 因此这里不提供"新建选项""删除选项"——那等于在 UI 层造出元数据域不认识的 value，
 * 运行时存进去的数据会破坏参照完整性。
 *
 * 覆写结果写入 schema.dictionaries，随 JSON 导出、可撤销、可发布。
 */
import { Alert, Button, Empty, Input, Switch, Tag, Tooltip, Typography } from 'antd';
import { RollbackOutlined } from '@ant-design/icons';
import { parseEnumValues } from '@bone/shared-types';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { clearDict, dictOf, setDictHidden, setDictLabel } from './schemaOps';

const { Text } = Typography;

interface Props {
  schema: BoneUiSchema;
  onChange: (next: BoneUiSchema) => void;
  fields: MetaField[];
  focus?: { key?: string };
}

export default function DictionaryPanel({ schema, onChange, fields, focus }: Props): React.ReactElement {
  const enumFields = fields.filter((f) => parseEnumValues(f.enumValues).length > 0);

  if (!enumFields.length) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="该实体没有带枚举值的字段，无需字典覆写"
        style={{ padding: '24px 0' }}
      />
    );
  }

  return (
    <div>
      <Alert
        type="info" showIcon style={{ marginBottom: 12 }}
        message="码表真源在元数据域"
        description="这里只能做协议允许的覆写：改显示名、隐藏选项。需要新增/删除选项请到字段的 enumValues 修改；需要按角色差异化请用「变体」而非在一份 schema 内做覆盖。"
      />

      {enumFields.map((f) => {
        const opts = parseEnumValues(f.enumValues);
        const ov = dictOf(schema, f.code);
        const ovCount = (ov?.hidden?.length ?? 0) + Object.keys(ov?.labelOverride ?? {}).length;
        const hiddenCount = ov?.hidden?.length ?? 0;

        return (
          <div
            key={f.code}
            style={{
              border: `1px solid ${focus?.key === f.code ? '#ffa940' : '#f0f0f0'}`,
              borderRadius: 10, marginBottom: 10, overflow: 'hidden', background: '#fff',
            }}
          >
            <div style={{
              display: 'flex', alignItems: 'center', gap: 8, padding: '8px 10px',
              borderBottom: '1px solid #f0f0f0', background: '#fafbfd',
            }}>
              <Text strong style={{ fontSize: 13 }}>{f.displayName}</Text>
              <Text code style={{ fontSize: 11 }}>{f.code}</Text>
              {ovCount > 0 && <Tag color="orange" style={{ fontSize: 11 }}>已覆写 {ovCount} 项</Tag>}
              {hiddenCount >= opts.length && <Tag color="red" style={{ fontSize: 11 }}>全部隐藏</Tag>}
              <span style={{ flex: 1 }} />
              <Text type="secondary" style={{ fontSize: 11 }}>
                共 {opts.length} 项 · 引用自元数据域（只读）
              </Text>
              {ovCount > 0 && (
                <Tooltip title="清除该字段的全部覆写">
                  <Button size="small" type="text" icon={<RollbackOutlined />}
                    onClick={() => onChange(clearDict(schema, f.code))}>
                    还原
                  </Button>
                </Tooltip>
              )}
            </div>

            <div style={{ padding: '8px 10px' }}>
              {opts.map((o) => {
                const hidden = (ov?.hidden ?? []).includes(o.code);
                return (
                  <div
                    key={o.code}
                    style={{
                      display: 'flex', alignItems: 'center', gap: 8,
                      padding: '5px 6px', borderRadius: 6, marginBottom: 3,
                      background: hidden ? '#fafafa' : undefined,
                      opacity: hidden ? 0.6 : 1,
                    }}
                  >
                    <Text code style={{ fontSize: 12, width: 120, flex: '0 0 auto' }}>{o.code}</Text>
                    <Text type="secondary" style={{ fontSize: 12, width: 130, flex: '0 0 auto' }}
                      delete={hidden}>
                      {o.label}
                    </Text>
                    <Input
                      size="small" style={{ flex: 1, minWidth: 120 }}
                      placeholder="覆盖显示名（留空沿用原名）"
                      value={ov?.labelOverride?.[o.code] ?? ''}
                      onChange={(e) => onChange(setDictLabel(schema, f.code, o.code, e.target.value))}
                    />
                    <Tooltip title="隐藏后该选项不在下拉中出现；历史数据中的旧值不受影响">
                      <Switch
                        size="small" checked={hidden}
                        onChange={(v) => onChange(setDictHidden(schema, f.code, o.code, v))}
                      />
                    </Tooltip>
                    <Text type="secondary" style={{ fontSize: 11, width: 32 }}>隐藏</Text>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
