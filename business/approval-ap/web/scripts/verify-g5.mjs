// M3 契约层验证：G5 业务摘要 displayFields + G4 结果枚举迁移。
// 覆盖：
//  G5-1 显式 displayFields → 详情/待办原样返回（截断 24 字符）
//  G5-2 未传 displayFields 但 formSchema.display:true → 从 formData 派生摘要
//  G5-3 二者皆无 → 空摘要（前端回退仅 title）
//  G5-4 待办卡片同样带 displayFields
//  G4   BPMN 终态事件已写入过去分词式 APPROVED/REJECTED（不再 APPROVE/REJECT）
// 运行：先启动 8085（dev-bypass，cim.auth.enabled=false），再 node verify-g5.mjs
const BASE = process.env.BASE || 'http://localhost:8085/api/v1/approval';

let pass = 0, fail = 0; const fails = [];
function ok(name, cond, extra = '') {
  if (cond) { pass++; console.log(`  ✅ ${name}${extra ? ' :: ' + extra : ''}`); }
  else { fail++; fails.push(name); console.log(`  ❌ ${name}${extra ? ' :: ' + extra : ''}`); }
}
async function call(path, method = 'GET', body, actor) {
  const sep = path.includes('?') ? '&' : '?';
  const url = actor ? path + sep + 'actor=' + encodeURIComponent(actor) : path;
  const res = await fetch(BASE + url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => null);
  return { status: res.status, body: json };
}
const data = (r) => r.body?.data;

async function start(extra) {
  const bk = 'g5-' + Date.now() + '-' + Math.floor(Math.random() * 1e6);
  const body = {
    processKey: 'singleApproval', businessKey: bk, tenantId: 'g5-domain',
    initiator: 'g5user', title: 'G5 摘要测试 ' + bk, assignee: 'g5approver', ...extra,
  };
  const r = await call('/processes/start', 'POST', body, 'g5user');
  return { bk, r };
}

async function main() {
  const domain = 'g5-domain';

  // G5-1 显式 displayFields
  {
    const df = [
      { key: 'woNo', label: '工单号', value: 'WO-88' },
      { key: 'amount', label: '金额', value: '3500.50' },
      { key: 'longval', label: '超长', value: '这是一段明显超过二十四字符长度的摘要内容应当被截断处理' },
    ];
    const { bk, r } = await start({ displayFields: df });
    ok('G5-1 发起 200', r.status === 200, 'code=' + r.status);
    const id = data(r)?.processInstanceId;
    const det = data(await call(`/processes/instances/${id}/detail?userId=g5user`, "GET"));
    ok('G5-1 详情返回 displayFields', !!det && Array.isArray(det.displayFields) && det.displayFields.length === 3,
      'len=' + (det?.displayFields?.length));
    ok('G5-1 值原样返回', det?.displayFields?.[0]?.value === 'WO-88' && det?.displayFields?.[0]?.label === '工单号');
    ok('G5-1 超长值被截断(≤25含…)', det?.displayFields?.[2]?.value?.length <= 25 && det?.displayFields?.[2]?.value?.endsWith('…'),
      'v=' + det?.displayFields?.[2]?.value);
  }

  // G5-2 派生自 formSchema.display:true + formData
  {
    const fd = { woNo: 'WO-99', amount: 1200, note: '内部备注' };
    const schema = [
      { key: 'woNo', label: '工单号', type: 'text', display: true },
      { key: 'amount', label: '金额', type: 'money', display: true },
      { key: 'note', label: '备注', type: 'text', display: false },
    ];
    const { r } = await start({ formData: fd, formSchema: schema });
    const id = data(r)?.processInstanceId;
    const det = data(await call(`/processes/instances/${id}/detail?userId=g5user`, "GET"));
    ok('G5-2 派生两条（display:true 命中）', det?.displayFields?.length === 2, 'len=' + det?.displayFields?.length);
    const keys = (det?.displayFields || []).map((f) => f.key).sort().join(',');
    ok('G5-2 仅含 display:true 字段', keys === 'amount,woNo', keys);
    ok('G5-2 值取 formData', det?.displayFields?.find((f) => f.key === 'woNo')?.value === 'WO-99');
    ok('G5-2 display:false 的 note 被排除', !(det?.displayFields || []).some((f) => f.key === 'note'));
  }

  // G5-3 二者皆无 → 空
  {
    const { r } = await start({});
    const id = data(r)?.processInstanceId;
    const det = data(await call(`/processes/instances/${id}/detail?userId=g5user`, "GET"));
    ok('G5-3 无摘要时 displayFields 为空数组', Array.isArray(det?.displayFields) && det.displayFields.length === 0,
      'len=' + det?.displayFields?.length);
  }

  // G5-4 待办卡片也带 displayFields
  {
    const df = [{ key: 'k', label: 'K', value: 'V' }];
    const { r } = await start({ displayFields: df });
    const id = data(r)?.processInstanceId;
    const todo = data(await call(`/tasks/todo?userId=g5approver&tenantId=${domain}`, 'GET', undefined, 'g5approver'));
    const hit = (todo || []).find((t) => t.processInstanceId === id);
    ok('G5-4 待办含该实例', !!hit);
    ok('G5-4 待办返回 displayFields', hit?.displayFields?.[0]?.value === 'V', 'v=' + hit?.displayFields?.[0]?.value);
  }

  // G4 BPMN 终态枚举已迁移为过去分词式
  {
    const defs = data(await call('/definitions', 'GET', undefined, 'admin')) || [];
    const single = (defs || []).find((d) => d.key === 'singleApproval');
    ok('G4 取到 singleApproval 定义', !!single, 'defId=' + single?.id);
    if (single) {
      const xml = data(await call(`/processes/${single.id}/xml`, 'GET', undefined, 'admin'));
      const raw = xml?.bpmnXml || '';
      ok('G4 BPMN 含 APPROVED（迁移后）', raw.includes("'APPROVED'"), 'len=' + raw.length);
      ok('G4 BPMN 不再含 APPROVE（旧值）', !raw.includes("setVariable('result','APPROVE')"));
      ok('G4 BPMN 含 REJECTED（迁移后）', raw.includes("'REJECTED'"));
    }
  }

  console.log(`\n结果：${pass} 通过 / ${fail} 失败` + (fails.length ? '  ⟵ ' + fails.join(', ') : ''));
  process.exit(fail === 0 ? 0 : 1);
}
main().catch((e) => { console.error(e); process.exit(1); });
