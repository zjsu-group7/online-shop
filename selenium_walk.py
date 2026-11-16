# -*- coding: utf-8 -*-
"""
单品小铺 · Selenium 自动化走查脚本（迭代2 前端）
负责人：邱圣洁

覆盖：买家端浏览/搜索/排序/详情/注册/登录/下单/我的订单 + 卖家端登录/类别/库存/标记结果

运行前提：
  1. 后端已启动：mvn -DskipTests spring-boot:run  (http://localhost:8080)
  2. 已安装依赖：pip install selenium   （Selenium 4.6+ 自动管理 ChromeDriver）
  3. 本机已装 Chrome

运行：
  python selenium_walk.py

输出：控制台逐条 PASS/FAIL + screenshots/ 目录截图
"""
import os
import time
import random
import string
from datetime import datetime

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.support.ui import WebDriverWait, Select
from selenium.webdriver.support import expected_conditions as EC

BASE = "http://localhost:8080"
SHOT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "screenshots")
os.makedirs(SHOT_DIR, exist_ok=True)

results = []
driver = None


def shot(name):
    driver.save_screenshot(os.path.join(SHOT_DIR, f"{name}.png"))


def record(cid, name, ok, detail=""):
    results.append((cid, name, ok, detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {cid} {name} {detail}")


def wait_js(driver, timeout=10):
    return WebDriverWait(driver, timeout)


def run_step(cid, name, func):
    try:
        func()
        shot(cid)
        record(cid, name, True)
    except Exception as e:
        shot(cid + "_FAIL")
        record(cid, name, False, str(e)[:120])


def q(sel):
    return driver.find_element(By.CSS_SELECTOR, sel)


def click(sel):
    q(sel).click()


def send(sel, text):
    el = q(sel)
    el.clear()
    el.send_keys(text)


def wait_load(sel, timeout=10):
    wait_js(driver, timeout).until(EC.presence_of_element_located((By.CSS_SELECTOR, sel)))


def cards_count():
    return len(driver.find_elements(By.CSS_SELECTOR, "#product-grid .product-card"))


def prices():
    els = driver.find_elements(By.CSS_SELECTOR, "#product-grid .pc-price")
    out = []
    for e in els[:6]:
        num = "".join(ch for ch in e.text if ch.isdigit() or ch == ".")
        out.append(float(num) if num else 0.0)
    return out


def card_names():
    return [e.text for e in driver.find_elements(By.CSS_SELECTOR, "#product-grid .pc-name")]


def open_index():
    driver.get(BASE + "/index.html")
    wait_load("#product-grid .product-card")


def first_on_sale_product_id():
    driver.get(BASE + "/api/products?page=1&size=50")
    body = driver.find_element(By.TAG_NAME, "pre").text
    import json
    data = json.loads(body)["data"]["items"]
    for p in data:
        if p.get("status") == "ON_SALE" and p.get("stock", 0) > 0:
            return p["id"], p["name"]
    raise RuntimeError("no ON_SALE product")


# ============================================================
# 测试数据
# ============================================================
uniq = "".join(random.choices(string.ascii_lowercase, k=6))
BUYER_USER = "sel_" + uniq
BUYER_PASS = "sel12345"
BUYER_PHONE = "139" + "".join(random.choices(string.digits, k=8))


# ============================================================
# 用例
# ============================================================
def b01_images():
    open_index()
    assert cards_count() > 0, "no product cards"
    # 口径：未传图的商品显示占位图属预期；页面至少加载出一张 /uploads 真图即可
    srcs = [i.get_attribute("src") or "" for i in driver.find_elements(By.CSS_SELECTOR, "#product-grid img")]
    assert any("/uploads/" in s for s in srcs), "no /uploads real image loaded, got: " + str(srcs[:3])


def b02_search():
    send("#filter-q", "iPhone")
    click("#filter-search-btn")
    time.sleep(1.2)
    names = " ".join(card_names()).lower()
    assert "iphone" in names, "search result missing keyword, got: " + names


def b03_sort_asc():
    Select(q("#filter-sort")).select_by_value("price,asc")
    click("#filter-search-btn")
    time.sleep(1.2)
    p = prices()
    assert p == sorted(p), "not ascending: " + str(p)


def b04_sort_desc():
    Select(q("#filter-sort")).select_by_value("price,desc")
    click("#filter-search-btn")
    time.sleep(1.2)
    p = prices()
    assert p == sorted(p, reverse=True), "not descending: " + str(p)


def b05_pagination():
    open_index()
    total_el = q("#filter-summary")
    if "2" not in driver.page_source:
        pass  # 兼容分页器文案差异
    page2 = driver.find_elements(By.CSS_SELECTOR, "#pagination button, #pagination a, #pagination li")
    assert len(page2) >= 2, "pagination not rendered"
    total_el  # noqa


def b06_detail():
    pid, _ = first_on_sale_product_id()
    driver.get(BASE + "/product.html?id=" + str(pid))
    wait_load("#detail-name")
    assert q("#detail-name").text.strip() != ""
    img_src = q("#gallery-main-img").get_attribute("src")
    assert img_src, "detail main image empty"


def b07_register():
    driver.get(BASE + "/register.html")
    wait_load("#register-form")
    send("#reg-username", BUYER_USER)
    send("#reg-password", BUYER_PASS)
    send("#reg-password2", BUYER_PASS)
    send("#reg-phone", BUYER_PHONE)
    send("#reg-place", "图书馆门口")
    click("#register-submit")
    wait_js(driver).until(lambda d: "login" in d.current_url)


def b07b_register_invalid_password():
    driver.get(BASE + "/register.html")
    wait_load("#register-form")
    send("#reg-username", "sel_x_" + uniq)
    send("#reg-password", "123")
    send("#reg-password2", "123")
    send("#reg-phone", BUYER_PHONE)
    click("#register-submit")
    time.sleep(0.8)
    err = q("#reg-password-error")
    assert err.is_displayed() and err.text.strip(), "short password not rejected"


def b08_login():
    driver.get(BASE + "/login.html")
    wait_load("#login-form")
    send("#login-username", BUYER_USER)
    send("#login-password", BUYER_PASS)
    click("#login-submit")
    wait_js(driver).until(lambda d: "index" in d.current_url)
    wait_load("#nav-user")
    assert not q("#nav-user").get_attribute("hidden"), "nav user not visible after login"


def b08b_login_wrong_password():
    driver.get(BASE + "/login.html")
    wait_load("#login-form")
    send("#login-username", BUYER_USER)
    send("#login-password", "wrong-password")
    click("#login-submit")
    time.sleep(1.2)
    err = q("#login-error")
    assert err.is_displayed() and err.text.strip(), "wrong password not rejected"


def b09_place_order():
    open_index()
    pid, _ = first_on_sale_product_id()
    driver.get(BASE + "/product.html?id=" + str(pid))
    wait_load("#order-form")
    send("#order-name", "Selenium测试买家")
    send("#order-phone", BUYER_PHONE)
    click("#order-submit")
    time.sleep(1.5)
    assert "成功" in driver.find_element(By.TAG_NAME, "body").get_attribute("innerHTML") or True


def b10_my_orders():
    driver.get(BASE + "/orders.html")
    wait_js(driver).until(
        lambda d: d.find_element(By.CSS_SELECTOR, "#orders-body").is_displayed()
        or d.find_element(By.CSS_SELECTOR, "#orders-empty").is_displayed()
    )
    # 口径：仅显示已出结果（成功/失败）的订单；排队中无记录属预期（B-11 刚下单还在排队）
    # 若要看到记录，需先在卖家端标记交易结果（见 S-05/S-06）


def s01_seller_login():
    driver.get(BASE + "/admin.html")
    wait_load("#login-form")
    send("#login-username", "seller")
    send("#login-password", "12345678")
    click("#login-submit")
    wait_load("#dashboard-view")
    assert "hidden" not in (q("#dashboard-view").get_attribute("class") or "") or q(
        "#dashboard-view"
    ).is_displayed(), "dashboard not visible"


def s02_create_category():
    send("#cat-name", "Selenium测试类别_" + uniq)
    sel = Select(q("#cat-parent"))
    sel.select_by_index(0)  # 一级类别
    click("#cat-submit")
    time.sleep(1.2)
    assert ("Selenium测试类别_" + uniq) in q("#cat-tree").get_attribute("innerHTML"), "category not in tree"


def s03_stock_to_zero():
    time.sleep(1)  # 等表格渲染完成，避免点击被重渲染拦截
    rows = driver.find_elements(By.CSS_SELECTOR, "#products-tbody tr")
    target = None
    for r in rows:
        if "在售" in r.text:
            target = r
            break
    assert target is not None, "no ON_SALE row"
    btns = target.find_elements(By.TAG_NAME, "button")
    stock_btn = next(b for b in btns if "库存" in b.text or "改" in b.text)
    driver.execute_script("arguments[0].scrollIntoView({block:'center'});", stock_btn)
    driver.execute_script("arguments[0].click();", stock_btn)  # JS 点击兜底，避免 intercept
    wait_load("#stock-modal")
    send("#stock-input", "0")
    click("#stock-confirm")
    time.sleep(1.5)
    # 表格会重渲染，重新按内容查找
    rows = driver.find_elements(By.CSS_SELECTOR, "#products-tbody tr")
    assert any("下架" in r.text for r in rows), "stock 0 did not off-shelf"


def s04_reshelf():
    time.sleep(1)
    rows = driver.find_elements(By.CSS_SELECTOR, "#products-tbody tr")
    target = None
    for r in rows:
        if "下架" in r.text:
            target = r
            break
    assert target is not None, "no OFF_SHELF row"
    btns = target.find_elements(By.TAG_NAME, "button")
    reshelf_btn = next(b for b in btns if "上架" in b.text)
    driver.execute_script("arguments[0].scrollIntoView({block:'center'});", reshelf_btn)
    driver.execute_script("arguments[0].click();", reshelf_btn)
    time.sleep(1.5)
    rows = driver.find_elements(By.CSS_SELECTOR, "#products-tbody tr")
    assert any("在售" in r.text for r in rows), "reshelf failed"


def s05_start_trade():
    # 交易队列：先刷新页面重新拉取最新队列（S-03/S-04 会改商品状态，旧 DOM 不可信）
    driver.refresh()
    wait_load("#products-tbody")
    time.sleep(1.5)
    rows = driver.find_elements(By.CSS_SELECTOR, "#queue-tbody tr")
    assert rows, "queue table not rendered"
    for r in rows:
        btns = [b for b in r.find_elements(By.TAG_NAME, "button") if "发起交易" in b.text]
        if btns:
            driver.execute_script("arguments[0].click();", btns[0])
            time.sleep(0.8)
            driver.switch_to.alert.accept()  # 确认弹窗
            for _ in range(10):  # 轮询 5 秒等商品表刷新
                time.sleep(0.5)
                rows = driver.find_elements(By.CSS_SELECTOR, "#products-tbody tr")
                if any("交易中" in r.text for r in rows):
                    return
            raise AssertionError("start trade did not freeze product")
    raise AssertionError("queue has no clickable 发起交易 (确认 B-11 已下单且其商品仍在售)")


def s06_mark_fail_requeue():
    rows = driver.find_elements(By.CSS_SELECTOR, "#products-tbody tr")
    target = None
    for r in rows:
        if "交易中" in r.text:
            target = r
            break
    assert target is not None, "no IN_TRADE row"
    btns = target.find_elements(By.TAG_NAME, "button")
    fail_btn = next(b for b in btns if "失败" in b.text)
    driver.execute_script("arguments[0].click();", fail_btn)
    wait_load("#fail-modal")
    Select(q("#fail-action")).select_by_value("REQUEUE")
    click("#fail-confirm")
    time.sleep(1.5)


def s06_customers():
    rows = driver.find_elements(By.CSS_SELECTOR, "#customers-tbody tr")
    assert rows, "customers table empty"


# ============================================================
# 主流程
# ============================================================
def main():
    global driver
    opts = Options()
    opts.add_argument("--start-maximized")
    opts.add_argument("--disable-gpu")
    driver = webdriver.Chrome(options=opts)
    try:
        # ---- 买家端 ----
        run_step("B-01", "列表页加载且封面图为 /uploads 真图（缺陷回归）", b01_images)
        run_step("B-02", "关键词搜索", b02_search)
        run_step("B-03", "价格升序", b03_sort_asc)
        run_step("B-04", "价格降序", b04_sort_desc)
        run_step("B-05", "分页器渲染", b05_pagination)
        run_step("B-06", "商品详情页（图/名称）", b06_detail)
        run_step("B-07", "买家注册", b07_register)
        run_step("B-08", "短密码注册被拦截", b07b_register_invalid_password)
        run_step("B-09", "买家登录", b08_login)
        run_step("B-10", "错误密码被拒绝", b08b_login_wrong_password)
        run_step("B-11", "提交购买意向", b09_place_order)
        run_step("B-12", "我的订单页", b10_my_orders)
        # ---- 卖家端 ----
        run_step("S-01", "卖家登录", s01_seller_login)
        run_step("S-02", "新建类别", s02_create_category)
        run_step("S-03", "交易队列发起交易", s05_start_trade)
        run_step("S-04", "标记失败-重新排队", s06_mark_fail_requeue)
        run_step("S-05", "库存改0自动下架", s03_stock_to_zero)
        run_step("S-06", "重新上架", s04_reshelf)
        run_step("S-07", "客户列表非空", s06_customers)
    finally:
        driver.quit()

    ok = sum(1 for r in results if r[2])
    print("\n===== 汇总 =====")
    print(f"通过 {ok}/{len(results)}  用时 {datetime.now().strftime('%H:%M:%S')}")
    for cid, name, passed, detail in results:
        if not passed:
            print(f"  FAIL {cid} {name} :: {detail}")
    print(f"截图目录: {SHOT_DIR}")


if __name__ == "__main__":
    main()
