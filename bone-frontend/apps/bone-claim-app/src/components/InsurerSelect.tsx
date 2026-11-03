import { useEffect, useState, type CSSProperties, type ReactElement } from 'react';
import { Select } from 'antd';
import { mappingApi, type InsurerView } from '../services/claimConfigApi';

interface InsurerOption {
  label: string;
  value: string;
  insurerName: string;
}

interface InsurerSelectProps {
  value?: string;
  onChange?: (insurerCode: string, insurerName: string) => void;
  style?: CSSProperties;
  placeholder?: string;
}

/**
 * 承保主体下拉（受控的保险公司主数据）。
 *
 * <p>多级分页拉取当前租户全部主体，供保单 / 映射关联选择。选择时回调 {@code (insurerCode, insurerName)}，
 * 调用方据此把保单的 `insurerName` 与 `bizIdentityCode` 一并写回——保单通过 `bizIdentityCode` 引用主体编码，
 * 而不是再手填自由文本（自由文本会让同一家保司分裂成多个映射键）。
 */
export function InsurerSelect({
  value,
  onChange,
  style,
  placeholder = '选择承保主体',
}: InsurerSelectProps): ReactElement {
  const [options, setOptions] = useState<InsurerOption[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const load = async (page: number, acc: InsurerOption[]) => {
      if (cancelled) {
        return;
      }
      setLoading(true);
      try {
        const res = await mappingApi.listInsurers({ page, size: 100 });
        if (res.code !== 200 || cancelled) {
          return;
        }
        const records = res.data.records as InsurerView[];
        const mapped = records.map((i) => ({
          label: i.insurerName + (i.insurerCode ? `（${i.insurerCode}）` : ''),
          value: i.insurerCode,
          insurerName: i.insurerName,
        }));
        const next = [...acc, ...mapped];
        if (records.length >= 100) {
          await load(page + 1, next);
        } else {
          setOptions(next);
        }
      } catch {
        // 下拉加载失败不影响其它表单交互
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    void load(1, []);
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <Select
      showSearch
      allowClear
      loading={loading}
      value={value || undefined}
      placeholder={placeholder}
      optionFilterProp="label"
      style={style}
      options={options}
      onChange={(v: string | undefined) => {
        const opt = options.find((o) => o.value === v);
        onChange?.(v ?? '', opt?.insurerName ?? '');
      }}
    />
  );
}
