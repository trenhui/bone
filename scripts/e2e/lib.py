import json,urllib.request,urllib.parse,urllib.error,hmac,hashlib,time,sys

GW="http://127.0.0.1:8888"
_t={}

def http(method,path,body=None,tenant="0",token=None,raw=False,headers=None,timeout=20):
    url=GW+path if path.startswith("/") else path
    data=None
    h={"Content-Type":"application/json"}
    if headers: h.update(headers)
    if token: h["Authorization"]="Bearer "+token
    if tenant is not None: h["X-Tenant-Id"]=str(tenant)
    if body is not None:
        data=json.dumps(body,ensure_ascii=False).encode()
    req=urllib.request.Request(url,data=data,headers=h,method=method)
    try:
        with urllib.request.urlopen(req,timeout=timeout) as r:
            raw_b=r.read()
            if raw: return r.status,raw_b
            return r.status,(json.loads(raw_b) if raw_b else None)
    except urllib.error.HTTPError as e:
        raw_b=e.read()
        try: j=json.loads(raw_b)
        except Exception: j={"_raw":raw_b.decode("utf-8","replace")}
        return e.code,j
    except Exception as e:
        return 0,{"_err":str(e)}

def login(u,p,tenant="0"):
    st,j=http("POST","/api/v1/iam/login",{"username":u,"password":p},tenant=tenant)
    if st!=200: raise SystemExit(f"login fail {st}: {json.dumps(j,ensure_ascii=False)[:300]}")
    return j["data"]["token"]

def show(tag,st,j,limit=280):
    s=json.dumps(j,ensure_ascii=False) if j is not None else ""
    print(f"  [{tag}] {st}  {s[:limit]}")
    return st,j

def hmac_sign(payment_id,trade_no,amount):
    payload=f"{payment_id}|{trade_no}|{str(amount)}"
    # amount 需 stripTrailingZeros().toPlainString()
    amt=amount.rstrip('0').rstrip('.') if '.' in str(amount) else str(amount)
    payload=f"{payment_id}|{trade_no}|{amt}"
    return hmac.new(b"bone-blueprint-mock-secret",payload.encode(),hashlib.sha256).hexdigest()
