import { useTranslation } from 'react-i18next';
import { Button, Space } from 'antd';

const LANGUAGES = [
  { code: 'zh', label: '中文' },
  { code: 'en', label: 'EN' },
] as const;

export default function LanguageSwitcher() {
  const { i18n } = useTranslation();

  return (
    <Space size={4}>
      {LANGUAGES.map(({ code, label }) => (
        <Button
          key={code}
          type={i18n.language?.startsWith(code) ? 'primary' : 'text'}
          size="small"
          onClick={() => i18n.changeLanguage(code)}
          style={{ padding: '0 8px', fontSize: 13 }}
        >
          {label}
        </Button>
      ))}
    </Space>
  );
}
