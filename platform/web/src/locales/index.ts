import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

// i18n 初始化骨架：热切换（平台前端文档 §6）。
// 资源后续按 locale 拆分加载；缺失文案四级兜底至默认中文。
i18n.use(initReactI18next).init({
  lng: 'zh-CN',
  fallbackLng: 'zh-CN',
  resources: {},
  interpolation: { escapeValue: false },
});

export default i18n;
