// 审批流平台 API 客户端。
//
// 后端所有接口位于 /api/v1/approval 前缀下（见 server 各 Controller 的 @RequestMapping）。
// 传输层（Bearer、`Result<T>` 解包、401 清态回登录页、`Accept-Language`）在 `@cim/data`
// 的 `createHttpClient` 里，四个 ap 共用一份；本文件只保留审批流自己的东西——资源前缀、
// 身份参数怎么取、以及各端点封装。
//
// 身份来源随认证通道切换（见 `currentIdentity`）：
// - 统一登录（cim.auth.enabled=true）：附 Bearer，后端 `CurrentUserResolver` 只认 JWT；
// - dev 态：以 userId/tenantId/roles 查询参数标识操作人。
// 两种情况下身份参数都照发：后端在认证态会忽略它们，保留则让滚动发布期间旧后端仍可用。
import { createHttpClient, type RequestOptions } from '@cim/data';
import { useAuthStore } from '@/store/authStore';
import { useIdentityStore, SHARED_DOMAIN } from '@/store/identityStore';
import type {
  AddSignRequest,
  AuditView,
  CallbackRegistrationRequest,
  CallbackRegistrationView,
  CompleteTaskRequest,
  DefinitionXmlView,
  FormSchemaField,
  OutboxView,
  ProcessDefinitionView,
  ProcessDetailView,
  ProcessInstanceView,
  ReassignRequest,
  ReturnRequest,
  ReturnTargetsView,
  StartProcessRequest,
  StatsView,
  TaskView,
  AttachmentView,
  MeDto,
} from '@/types';

export type { ApiError, RequestOptions } from '@cim/data';

/**
 * ⚠️ 与 `store/authStore.ts` 是**循环 import**（它要 `http` 发 `/me` 与登出）。
 * 能成立是因为 `getToken` / `onUnauthorized` 都是闭包，只在发请求时才求值。
 */
const http = createHttpClient({
  // 与另外三端不同：approval 的资源前缀是 `/api/v1/approval`，页面里只写 `/definitions` 这类资源段
  baseURL: '/api/v1/approval',
  getToken: () => useAuthStore.getState().token,
  // dev 通道下 token 恒为 null，清态是空操作；身份在 `identityStore`，不受影响。
  onUnauthorized: () => useAuthStore.getState().clear(),
  loginPath: '/login',
});

/**
 * 当前操作人身份。
 *
 * <p>统一登录态取 JWT 档案（`/me` 已写入 authStore）；dev 态取身份切换器维护的 store。
 * 页面一律不必关心来源——`identityStore` 在两种通道下都持有「当前我是谁」。</p>
 */
function currentIdentity(): { userId: string; tenantId: string; roles: string[] } {
  const { mode, user } = useAuthStore.getState();
  if (mode === 'sso' && user) {
    // tenantId 缺省回落共享域：域是流程变量，存量实例都在共享域；后端会校验它是否在令牌域内
    return { userId: user.userId ?? '', tenantId: user.tenantId || SHARED_DOMAIN, roles: user.roles ?? [] };
  }
  const { userId, tenantId, roles } = useIdentityStore.getState();
  return { userId, tenantId, roles: roles ?? [] };
}

// ---------- 当前用户 / 认证通道 ----------

/**
 * 启动探针：判定认证通道。
 *
 * <p>走 `HttpClient#fetchMe`（内部用裸 axios）：探针在「没有令牌」时调用是正常路径，
 * 走带拦截器的实例会被 401 当成会话失效直接跳登录页，反而分不清「未登录」与「后端关了认证」。</p>
 *
 * @returns 后端档案；401（认证已开启但未登录 / 令牌失效）返回 null
 */
export function fetchMe(): Promise<MeDto | null> {
  return http.fetchMe<MeDto>('/api/v1/approval/me');
}

/** 拉取当前用户档案（已登录；走带拦截器的实例，401 会清态跳登录）。 */
export function getMe(opts?: RequestOptions): Promise<MeDto | null> {
  return apiGet<MeDto | null>('/me', undefined, opts);
}

async function apiGet<T>(url: string, params?: Record<string, unknown>, opts?: RequestOptions): Promise<T> {
  return http.get<T>(url, params, opts);
}

async function apiPost<T>(url: string, body?: unknown, params?: Record<string, unknown>): Promise<T> {
  return http.post<T>(url, body, params ? { params } : undefined);
}

// ---------- 流程定义 / 发起 ----------

export function listDefinitions(opts?: RequestOptions): Promise<ProcessDefinitionView[]> {
  return apiGet<ProcessDefinitionView[]>('/definitions', undefined, opts);
}

export function getDefinitionXml(definitionId: string, opts?: RequestOptions): Promise<DefinitionXmlView> {
  return apiGet<DefinitionXmlView>(`/processes/${encodeURIComponent(definitionId)}/xml`, undefined, opts);
}

export function startProcess(req: StartProcessRequest): Promise<{ processInstanceId: string }> {
  return apiPost<{ processInstanceId: string }>('/processes/start', req);
}

/** 撤回：发起人终止运行中实例。 */
export function withdrawInstance(processInstanceId: string, actor?: string): Promise<void> {
  return http.post(`/processes/instances/${encodeURIComponent(processInstanceId)}/withdraw`, null, {
    params: { actor: actor ?? currentIdentity().userId },
  });
}

/** 催办：向当前待办人推送实时催办通知。 */
export function urgeInstance(processInstanceId: string, actor?: string): Promise<void> {
  return http.post(`/processes/instances/${encodeURIComponent(processInstanceId)}/urge`, null, {
    params: { actor: actor ?? currentIdentity().userId },
  });
}

// ---------- 运行时部署（拖拽设计器导出） ----------

export interface DeployProcessRequest {
  processKey: string;
  processName: string;
  bpmnXml: string;
}

export function deployProcess(req: DeployProcessRequest): Promise<ProcessDefinitionView> {
  return apiPost<ProcessDefinitionView>('/processes/deploy', req);
}

// ---------- 任务 ----------

export function listTodo(userId?: string, tenantId?: string, opts?: RequestOptions): Promise<TaskView[]> {
  const { userId: id, tenantId: tn, roles } = currentIdentity();
  return apiGet<TaskView[]>('/tasks/todo', { userId: userId ?? id, tenantId: tenantId ?? tn, roles }, opts);
}

export function listDone(userId?: string, tenantId?: string, opts?: RequestOptions): Promise<TaskView[]> {
  const { userId: id, tenantId: tn, roles } = currentIdentity();
  return apiGet<TaskView[]>('/tasks/done', { userId: userId ?? id, tenantId: tenantId ?? tn, roles }, opts);
}

export function listInitiated(userId?: string, tenantId?: string, opts?: RequestOptions): Promise<ProcessInstanceView[]> {
  const id = userId ?? currentIdentity().userId;
  const tn = tenantId ?? currentIdentity().tenantId;
  return apiGet<ProcessInstanceView[]>('/tasks/initiated', { userId: id, tenantId: tn }, opts);
}

export function listTodoPaged(page1: number, size: number, opts?: RequestOptions): Promise<PageResult<TaskView>> {
  const { userId, tenantId, roles } = currentIdentity();
  return apiGet<PageResult<TaskView>>(`/tasks/todo/page?page=${page1}&size=${size}`, { userId, tenantId, roles }, opts);
}
export function listDonePaged(page1: number, size: number, opts?: RequestOptions): Promise<PageResult<TaskView>> {
  const { userId, tenantId, roles } = currentIdentity();
  return apiGet<PageResult<TaskView>>(`/tasks/done/page?page=${page1}&size=${size}`, { userId, tenantId, roles }, opts);
}
export function listInitiatedPaged(page1: number, size: number, opts?: RequestOptions): Promise<PageResult<ProcessInstanceView>> {
  const { userId, tenantId } = currentIdentity();
  return apiGet<PageResult<ProcessInstanceView>>(`/processes/initiated/page?page=${page1}&size=${size}`, { userId, tenantId }, opts);
}
// 平台分页结构（cim-core PageResult：records/total/page/size）
export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}
export function batchComplete(body: { taskIds: string[]; outcome: string; comment?: string; actor: string })
  : Promise<{ taskId: string; ok: boolean; error: string | null }[]> {
  return apiPost('/tasks/batch-complete', body);
}
export function delegateTask(taskId: string, body: { assignee: string; reason?: string; actor: string }): Promise<void> {
  return apiPost(`/tasks/${encodeURIComponent(taskId)}/delegate`, body);
}
export function resolveTask(taskId: string, body: { comment?: string; actor: string }): Promise<void> {
  return apiPost(`/tasks/${encodeURIComponent(taskId)}/resolve`, body);
}
export function getStats(days = 30, opts?: RequestOptions): Promise<StatsView> {
  return apiGet(`/stats?days=${days}`, undefined, opts);
}
export function suspendDefinition(definitionId: string, suspend: boolean): Promise<void> {
  return apiPost(`/processes/${encodeURIComponent(definitionId)}/suspend?suspend=${suspend}`, null);
}
export function duplicateDefinition(definitionId: string): Promise<ProcessDefinitionView> {
  return apiPost<ProcessDefinitionView>(`/processes/${encodeURIComponent(definitionId)}/duplicate`);
}
export function getOutboxStats(opts?: RequestOptions): Promise<Record<string, number>> {
  return apiGet('/callbacks/outbox/stats', undefined, opts);
}
export function testCallback(appCode: string): Promise<{ messageId: string; status: string; lastError: string | null }> {
  return apiPost(`/callbacks/test/${encodeURIComponent(appCode)}`);
}
export function completeTask(taskId: string, req: CompleteTaskRequest): Promise<void> {
  const { roles } = currentIdentity();
  return apiPost<void>(`/tasks/${taskId}/complete`, req, { roles });
}

export function returnTask(taskId: string, req: ReturnRequest): Promise<void> {
  const { roles } = currentIdentity();
  return apiPost<void>(`/tasks/${taskId}/return`, req, { roles });
}

/** 退回弹窗数据源：目标节点选项 + 轮次/上限 + 并行段状态。 */
export function getReturnTargets(taskId: string, opts?: RequestOptions): Promise<ReturnTargetsView> {
  return apiGet<ReturnTargetsView>(`/tasks/${encodeURIComponent(taskId)}/return-targets`, undefined, opts);
}

/** 重新提交：发起人修改表单后重提（退回到发起人后的重提态），审批链从头重走。 */
export function resubmitInstance(
  processInstanceId: string,
  req: { formData?: Record<string, unknown>; formSchema?: FormSchemaField[]; actor?: string },
): Promise<void> {
  return apiPost<void>(
    `/processes/instances/${encodeURIComponent(processInstanceId)}/resubmit`,
    { ...req, actor: req.actor ?? currentIdentity().userId },
  );
}

export function addSignTask(taskId: string, req: AddSignRequest): Promise<void> {
  const { roles } = currentIdentity();
  return apiPost<void>(`/tasks/${taskId}/add-sign`, req, { roles });
}

export function reassignTask(taskId: string, req: ReassignRequest): Promise<void> {
  const { roles } = currentIdentity();
  return apiPost<void>(`/tasks/${taskId}/reassign`, req, { roles });
}

// ---------- 回调注册 / 查询 ----------

export function registerCallback(req: CallbackRegistrationRequest): Promise<CallbackRegistrationView> {
  const qs = req.confirmToken ? `?confirmToken=${encodeURIComponent(req.confirmToken)}` : '';
  return apiPost<CallbackRegistrationView>(`/callbacks/register${qs}`, req);
}

export function listCallbacks(opts?: RequestOptions): Promise<CallbackRegistrationView[]> {
  return apiGet<CallbackRegistrationView[]>('/callbacks', undefined, opts);
}

export function queryOutbox(
  appCode?: string,
  businessKey?: string,
  opts?: RequestOptions,
): Promise<OutboxView[]> {
  return apiGet<OutboxView[]>('/callbacks/outbox', { appCode, businessKey }, opts);
}

export function deliverMessage(messageId: string): Promise<void> {
  return apiPost<void>(`/callbacks/deliver/${messageId}`);
}

// ---------- 审批详情 / 附件 ----------

export function getProcessDetail(
  processInstanceId: string,
  userId?: string,
  opts?: RequestOptions,
): Promise<ProcessDetailView> {
  return apiGet<ProcessDetailView>(
    `/processes/instances/${encodeURIComponent(processInstanceId)}/detail`,
    { userId: userId ?? currentIdentity().userId },
    opts,
  );
}

export function listAttachments(businessKey: string, opts?: RequestOptions): Promise<AttachmentView[]> {
  return apiGet<AttachmentView[]>('/attachments', { businessKey }, opts);
}

export async function uploadAttachment(
  file: File,
  businessKey: string,
  opts?: { processInstanceId?: string; tenantId?: string; uploader?: string },
): Promise<AttachmentView> {
  const form = new FormData();
  form.append('file', file);
  form.append('businessKey', businessKey);
  if (opts?.processInstanceId) form.append('processInstanceId', opts.processInstanceId);
  form.append('tenantId', opts?.tenantId ?? currentIdentity().tenantId);
  form.append('uploader', opts?.uploader ?? currentIdentity().userId);
  const r = await http.post<AttachmentView>('/attachments/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r;
}

export function deleteAttachment(id: string, actor?: string): Promise<void> {
  return http.del<void>(`/attachments/${encodeURIComponent(id)}`, {
    actor: actor ?? currentIdentity().userId,
  });
}

export function attachmentDownloadUrl(id: string): string {
  const base = `/api/v1/approval/attachments/${encodeURIComponent(id)}/download`;
  // `<a download>` 带不了 Authorization 头：统一登录态走 `?token=`（后端 /api 链专门为此放行），
  // 与 SSE 同一处理方式（见 ApprovalSecurityConfig）。
  const { mode, token } = useAuthStore.getState();
  return mode === 'sso' && token ? `${base}?token=${encodeURIComponent(token)}` : base;
}

// ---------- 审计 ----------

export function listAudit(businessKey: string, opts?: RequestOptions): Promise<AuditView[]> {
  return apiGet<AuditView[]>('/audit', { businessKey }, opts);
}

// ---------- SSE 通知 ----------
//
// 统一经 cim-notify-starter 的 /sys/notify/stream 端点（平台前缀 /sys/**）。
// EventSource 无法自定义请求头，故两种通道各用自己的凭据载体：
// - 统一登录：`?token=<JWT>`（后端 BearerTokenQueryParamFilter 还原为 Bearer 头）；
// - dev 态：`?userId=&tenantId=`（cim.notify.dev-accept-query-identity）。
export function notificationsStreamUrl(): string {
  const { mode, token } = useAuthStore.getState();
  if (mode === 'sso' && token) {
    return `/sys/notify/stream?token=${encodeURIComponent(token)}`;
  }
  const { userId, tenantId } = useIdentityStore.getState();
  const params = new URLSearchParams({ userId, tenantId });
  return `/sys/notify/stream?${params.toString()}`;
}

// ---------- 企微免登（G8 / D9） ----------
// 生成绑定当前用户 + 任务的短期签名链接；企微内置浏览器打开后后端核销免登录进入办理。
export function createWeComLink(taskId: string, actor?: string): Promise<{ token: string; link: string }> {
  const params = actor ? `?userId=${encodeURIComponent(actor)}` : '';
  return apiPost<{ token: string; link: string }>(`/tasks/${taskId}/wecom-link${params}`);
}

export function verifyWeComToken(token: string): Promise<{ userId: string; taskId: string; valid: boolean }> {
  return apiGet<{ userId: string; taskId: string; valid: boolean }>(
    `/wecom/callback?token=${encodeURIComponent(token)}`,
  );
}

export default {
  fetchMe,
  getMe,
  listDefinitions,
  getDefinitionXml,
  startProcess,
  deployProcess,
  listTodo,
  listDone,
  listInitiated,
  completeTask,
  returnTask,
  getReturnTargets,
  resubmitInstance,
  addSignTask,
  reassignTask,
  registerCallback,
  listCallbacks,
  queryOutbox,
  deliverMessage,
  listAudit,
  getProcessDetail,
  listAttachments,
  uploadAttachment,
  deleteAttachment,
  attachmentDownloadUrl,
  notificationsStreamUrl,
  createWeComLink,
  verifyWeComToken,
};
