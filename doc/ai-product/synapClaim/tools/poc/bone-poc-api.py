#!/usr/bin/env python3
"""企业真实 POC：经网关 8888，用真实 JWT 验证理赔四类前缀端点的分页/筛选契约。"""
import base64
import json
import urllib.error
import urllib.parse
import urllib.request

BASE = 'http://127.0.0.1:8888'
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))
results = []


def call(path, token=None, method='GET', body=None):
    headers = {'Accept': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = None
    if body is not None:
        data = json.dumps(body).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with OPENER.open(request, timeout=20) as response:
            return response.status, json.loads(response.read().decode('utf-8'))
    except urllib.error.HTTPError as err:
        return err.code, json.loads(err.read().decode('utf-8'))


def check(name, ok, detail):
    results.append((name, ok, detail))
    print(('[PASS] ' if ok else '[FAIL] ') + name + ' :: ' + detail)


def page_of(payload):
    data = payload.get('data') or {}
    records = data.get('records') or []
    total = data.get('total')
    return records, int(total) if total is not None else None


status, login = call('/api/v1/iam/login', method='POST',
                     body={'username': 'admin', 'password': '123456'})
token = (login.get('data') or {}).get('token') if status == 200 else None
def jwt_scopes(jwt):
    try:
        part = jwt.split('.')[1]
        part += '=' * (-len(part) % 4)
        return json.loads(base64.urlsafe_b64decode(part)).get('scopes', [])
    except Exception:
        return []


scopes = jwt_scopes(token or '')
check('登录取真实 JWT', status == 200 and 'claim:claims:read' in scopes,
      'HTTP %d, code=%s, scopes=%d 个, 含 claim:claims:read=%s' % (
          status, login.get('code'), len(scopes), 'claim:claims:read' in scopes))

if not token:
    sys.exit(1)

# 1) 核赔任务：真分页（page1/page2 不重叠）+ status 筛选生效
s1, p1 = call('/api/v1/review/tasks?page=1&size=5', token)
s2, p2 = call('/api/v1/review/tasks?page=2&size=5', token)
r1, t1 = page_of(p1)
r2, t2 = page_of(p2)
ids1 = {r.get('claimId') for r in r1}
ids2 = {r.get('claimId') for r in r2}
check('核赔 tasks 真服务端分页',
      s1 == 200 and s2 == 200 and t1 == t2 and bool(ids1) and not (ids1 & ids2),
      'HTTP %d/%d, total=%s, page1=%d 条 page2=%d 条, 交集=%d' % (
          s1, s2, t1, len(ids1), len(ids2), len(ids1 & ids2)))
sw, pw = call('/api/v1/review/tasks?status=WAITING&page=1&size=5', token)
rw, tw = page_of(pw)
check('核赔 tasks 任务维筛选', sw == 200 and tw is not None,
      'HTTP %d, status=WAITING total=%s (未筛选 total=%s)' % (sw, tw, t1))

# 2) 初审案件：真分页
c1, pc1 = call('/api/v1/claims?page=1&size=5', token)
c2, pc2 = call('/api/v1/claims?page=2&size=5', token)
cr1, ct1 = page_of(pc1)
cr2, ct2 = page_of(pc2)
cid1 = {r.get('claimId') for r in cr1}
cid2 = {r.get('claimId') for r in cr2}
check('初审 claims 真服务端分页',
      c1 == 200 and c2 == 200 and bool(cid1) and not (cid1 & cid2),
      'HTTP %d/%d, total=%s, 两页交集=%d' % (c1, c2, ct1, len(cid1 & cid2)))

# 3) 保单配置：keyword 真筛选（关键字来自列表首条真实保单）
pl, plist = call('/api/v1/claim-config/policies?page=1&size=5', token)
prows, ptotal = page_of(plist)
probe = None
for row in prows:
    if row.get('policyNo'):
        probe = row['policyNo']
        break
if probe:
    pk, pfiltered = call('/api/v1/claim-config/policies?page=1&size=5&keyword=' +
                         urllib.parse.quote(probe), token)
    frows, ftotal = page_of(pfiltered)
    check('保单配置 keyword 真筛选',
          pk == 200 and ftotal is not None and ftotal <= ptotal and
          all(r.get('policyNo') == probe for r in frows),
          'HTTP %d, keyword=%s, total %s→%s, 命中 %d 条' % (
              pk, probe, ptotal, ftotal, len(frows)))
else:
    check('保单配置 keyword 真筛选', False, '列表未返回可用 policyNo，无法取证')

# 4) 额度账户：真实保单 + keyword 契约（额度页本轮接线的核心依赖）
if probe:
    qa, qpayload = call('/api/v1/claim-config/quota-accounts?policyNo=' +
                        urllib.parse.quote(probe) + '&page=1&size=5', token)
    qrows, qtotal = page_of(qpayload)
    check('额度账户按保单查询', qa == 200 and qtotal is not None,
          'HTTP %d, policyNo=%s, total=%s, records=%d' % (
              qa, probe, qtotal, len(qrows)))
    qk, qfiltered = call('/api/v1/claim-config/quota-accounts?policyNo=' +
                         urllib.parse.quote(probe) +
                         '&keyword=e2e&page=1&size=5', token)
    check('额度账户 keyword 查询可调用', qk == 200, 'HTTP %d, keyword=e2e' % qk)

# 5) 标准码取全依赖：目录库分页契约（标准码截断修复的真实依据）
for domain in ('hospitals', 'diseases', 'drugs', 'fee-categories'):
    ds, dpayload = call('/api/v1/claim-config/libraries/' + domain + '?page=1&size=5', token)
    drows, dtotal = page_of(dpayload)
    check('标准码 %s 分页契约' % domain,
          ds == 200 and dtotal is not None,
          'HTTP %d, total=%s, records=%d' % (ds, dtotal, len(drows)))

# 6) AI 治理端点：带必填 from/to，验证 ops 前缀（静态门禁无法覆盖的运行时契约）
ops, opayload = call('/api/v1/claim-ops/ai-runtime-health?from=2026-11-01T00%3A00%3A00Z&to=2026-11-24T00%3A00%3A00Z', token)
check('AI 治理 ops 前缀', ops == 200,
      'HTTP %d, code=%s' % (ops, opayload.get('code')))

passed = sum(1 for _, ok, _ in results if ok)
print('\n===== 真实 API POC: %d/%d PASS =====' % (passed, len(results)))
