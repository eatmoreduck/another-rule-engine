import React from 'react';
import ReactDOM from 'react-dom/client';
import '@ant-design/v5-patch-for-react-19';
import { RouterProvider } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import { useTranslation } from 'react-i18next';
import router from './App';
import './i18n';
import './styles/editor.css';

const ANT_LOCALES: Record<string, typeof zhCN> = {
  zh: zhCN,
  en: enUS,
};

function AppWithI18n() {
  const { i18n } = useTranslation();
  const antLocale = ANT_LOCALES[i18n.language?.split('-')[0] ?? 'zh'] ?? zhCN;

  return (
    <ConfigProvider locale={antLocale}>
      <RouterProvider router={router} />
    </ConfigProvider>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppWithI18n />
  </React.StrictMode>
);
