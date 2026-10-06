import { useEffect, useMemo, useRef, useState } from 'react';
import { AutoComplete, Tag, Tooltip } from 'antd';
import type { CSSProperties } from 'react';
import { InfoCircleOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { resolveFeatureField, searchFeatureDefinitions } from '../../api/featureCatalog';
import type { FeatureDefinition, FeatureResolvedInfo } from '../../types/featureCatalog';

interface FeatureFieldInputProps {
  value?: string;
  onChange: (value: string) => void;
  onFeatureResolved?: (resolved: FeatureResolvedInfo | null) => void;
  onResolvingChange?: (resolving: boolean) => void;
  placeholder?: string;
  style?: CSSProperties;
  size?: 'small' | 'middle' | 'large';
}

function normalizeText(input: string | undefined): string {
  return (input ?? '').trim().toLowerCase();
}

export default function FeatureFieldInput({
  value,
  onChange,
  onFeatureResolved,
  onResolvingChange,
  placeholder,
  style,
  size = 'middle',
}: FeatureFieldInputProps) {
  const { t } = useTranslation();
  const [options, setOptions] = useState<FeatureDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [resolving, setResolving] = useState(false);
  const requestIdRef = useRef(0);
  const keyword = value?.trim() ?? '';

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      setLoading(true);
      try {
        const result = await searchFeatureDefinitions(keyword, 20);
        if (!cancelled) {
          setOptions(result);
        }
      } catch {
        if (!cancelled) {
          setOptions([]);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }, 200);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [keyword]);

  useEffect(() => {
    const input = value?.trim() ?? '';
    if (!input) {
      onFeatureResolved?.(null);
      return;
    }

    const normalizedInput = normalizeText(input);
    const direct = options.find((item) => normalizeText(item.code) === normalizedInput);
    if (direct) {
      onFeatureResolved?.({
        feature: direct,
        matchedByAlias: false,
      });
      return;
    }

    const aliasMatch = options.find((item) =>
      item.aliases.some((alias) => normalizeText(alias) === normalizedInput));
    if (aliasMatch) {
      const matchedAlias = aliasMatch.aliases.find((alias) => normalizeText(alias) === normalizedInput);
      onFeatureResolved?.({
        feature: aliasMatch,
        matchedByAlias: true,
        matchedAlias,
      });
      return;
    }

    if (input.length < 2) {
      onFeatureResolved?.(null);
      return;
    }

    let cancelled = false;
    const currentId = ++requestIdRef.current;
    const timer = window.setTimeout(async () => {
      setResolving(true);
      onResolvingChange?.(true);
      try {
        const resolved = await resolveFeatureField(input);
        if (!cancelled && currentId === requestIdRef.current) {
          onFeatureResolved?.(resolved);
        }
      } catch {
        if (!cancelled && currentId === requestIdRef.current) {
          onFeatureResolved?.(null);
        }
      } finally {
        if (!cancelled && currentId === requestIdRef.current) {
          setResolving(false);
          onResolvingChange?.(false);
        }
      }
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [onFeatureResolved, onResolvingChange, options, value]);

  const handleSelect = (selectedCode: string) => {
    const normalized = normalizeText(selectedCode);
    const selected = options.find((item) => normalizeText(item.code) === normalized);
    if (selected) {
      onFeatureResolved?.({
        feature: selected,
        matchedByAlias: false,
      });
    }
    onChange(selectedCode);
  };

  const autocompleteOptions = useMemo(() => options.map((item) => ({
    value: item.code,
    label: (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
          <div>
            <div style={{ fontWeight: 600 }}>{item.code}</div>
            <div style={{ fontSize: 12, color: '#8c8c8c' }}>{item.name}</div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <Tag color="blue" style={{ marginInlineEnd: 0 }}>{item.dataType}</Tag>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {item.aliases.length > 0 && (
            <div style={{ fontSize: 12, color: '#8c8c8c' }}>
              {t('featureCatalog.aliasesShortLabel')}: {item.aliases.slice(0, 3).join(', ')}
              {item.aliases.length > 3 ? ' ...' : ''}
            </div>
          )}
          {item.description && (
            <Tooltip title={item.description}>
              <InfoCircleOutlined style={{ color: '#8c8c8c' }} />
            </Tooltip>
          )}
        </div>
      </div>
    ),
  })), [options]);

  return (
    <AutoComplete
      value={value}
      options={autocompleteOptions}
      onSearch={onChange}
      onChange={onChange}
      onSelect={handleSelect}
      placeholder={placeholder}
      style={style}
      size={size}
      allowClear
      filterOption={false}
      notFoundContent={loading || resolving ? t('featureCatalog.searching') : t('featureCatalog.noMatchedFeatures')}
    />
  );
}
