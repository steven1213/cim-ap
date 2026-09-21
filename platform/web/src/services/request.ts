import axios from 'axios';

// 请求层骨架：Axios 实例 + 拦截器。
// 后续按平台前端文档 §2 补全：401 无感刷新 / 并发去重 / 统一错误处理 / 幂等键。
const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

request.interceptors.request.use((config) => config);

request.interceptors.response.use(
  (response) => response.data,
  (error) => Promise.reject(error),
);

export default request;
