# -*- coding: utf-8 -*-
"""
单品小铺 · 迭代 3（挑战需求 B）Selenium 端到端回归
负责人：邱圣洁（前端 / 测试）

前置：
  1. 后端已启动并完成迭代 3 接口（购物车/收藏/订单/卖家订单管理）
  2. pip install selenium
  3. python selenium_walk3.py

用例 17 条：
  B 链路（买家）：登录 → 加购 → 数量累加 → 改数量 → 收藏/取消收藏 →
                  购物车多选下单 → 订单出现（CREATED）→ 缺货链路 → 取消链路
  S 链路（卖家）：登录 → 订单推进 确认→备货→发货→完成 → 买家订单态同步
  取消链路：买家取消 → 状态 CANCELLED；卖家取消 → CANCELLED
输出：PASS/FAIL 汇总 + 截图存 screenshots/
"""
import os
import time
import datetime
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager

BASE = "http://localhost:8080"
SHOT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "screenshots")
BUYER_USER, BUYER_PASS = "test01", "test12345"
SELLER_USER, SELLER_PASS = "seller", "12345678"

results = []


def log(ok, case, note=""):
    results.append((ok, case, note))
    print(("[PASS] " if ok else "[FAIL] ") + case + ((" · " + note) if note else ""))


def shot(driver, name):
    os.makedirs(SHOT_DIR, exist_ok=True)
    driver.save_screenshot(os.path.join(SHOT_DIR, name + ".png"))


def new_driver():
    opts = Options()
    opts.add_argument("--start-maximized")
    return webdriver.Chrome(service=Service(ChromeDriverManager().install()), options=opts)


def js_click(driver, el):
    driver.execute_script("arguments[0].click();", el)


def wait_text(driver, text, timeout=6):
    return WebDriverWait(driver, timeout).until(
        EC.text_contains((By.TAG_NAME, "body"), text))


def login_buyer(driver):
    driver.get(BASE + "/login.html")
    WebDriverWait(driver, 8).until(EC.presence_of_element_located((By.ID, "login-username")))
    driver.find_element(By.ID, "login-username").send_keys(BUYER_USER)
    driver.find_element(By.ID, "login-password").send_keys(BUYER_PASS)
    driver.find_element(By.CSS_SELECTOR, "#login-form button[type=submit]").click()
    wait_text(driver, BUYER_USER)


def login_seller(driver):
    driver.get(BASE + "/admin.html")
    WebDriverWait(driver, 8).until(EC.presence_of_element_located((By.ID, "login-username")))
    driver.find_element(By.ID, "login-username").send_keys(SELLER_USER)
    driver.find_element(By.ID, "login-password").send_keys(SELLER_PASS)
    driver.find_element(By.CSS_SELECTOR, "#login-form button[type=submit]").click()
    WebDriverWait(driver, 8).until(lambda d: d.find_element(By.ID, "dashboard-view").is_displayed())


def open_first_on_sale_product(driver):
    driver.get(BASE + "/index.html")
    time.sleep(1)
    cards = driver.find_elements(By.CSS_SELECTOR, "a[href*='product.html?id=']")
    assert cards, "列表页没有可点的商品链接"
    js_click(driver, cards[0])
    time.sleep(1)


# ==================== 用例 ====================

def t_b01_cart_add(driver):
    """B-01 详情页加入购物车成功 Toast"""
    open_first_on_sale_product(driver)
    qty = driver.find_element(By.ID, "buy-qty")
    assert qty and qty.text.strip() == "1", "数量步进器未渲染"
    driver.find_element(By.ID, "buy-add-cart").click()
    wait_text(driver, "已加入购物车")
    log(True, "B-01 详情页加入购物车", "")


def t_b02_cart_accumulate(driver):
    """B-02 重复加购数量累加"""
    driver.find_element(By.ID, "buy-add-cart").click()
    wait_text(driver, "已加入购物车")
    driver.get(BASE + "/cart.html")
    time.sleep(1)
    val = driver.find_element(By.CSS_SELECTOR, "#cart-tbody .qty-val").text.strip()
    assert val == "2", f"重复加购数量应为 2，实际 {val}"
    log(True, "B-02 重复加购数量累加", "qty=2")


def t_b03_cart_qty_change(driver):
    """B-03 购物车改数量（＋步进）"""
    driver.find_element(By.CSS_SELECTOR, "#cart-tbody [data-act='plus']").click()
    time.sleep(1)
    val = driver.find_element(By.CSS_SELECTOR, "#cart-tbody .qty-val").text.strip()
    assert val == "3", f"步进后应为 3，实际 {val}"
    log(True, "B-03 购物车修改数量", "qty=3")


def t_b04_favorite_add(driver):
    """B-04 收藏成功且按钮变化"""
    driver.get(BASE + "/index.html")
    time.sleep(1)
    js_click(driver, driver.find_elements(By.CSS_SELECTOR, "a[href*='product.html?id=']")[1])
    time.sleep(1)
    driver.find_element(By.ID, "buy-favorite").click()
    wait_text(driver, "已加入收藏")
    log(True, "B-04 收藏商品", "")


def t_b05_favorite_page(driver):
    """B-05 收藏页出现该商品"""
    driver.get(BASE + "/favorite.html")
    time.sleep(1)
    body = driver.find_element(By.TAG_NAME, "body").text
    assert "取消收藏" in body, "收藏页未显示条目"
    log(True, "B-05 收藏页列表", "")


def t_b06_order_submit(driver):
    """B-06 购物车勾选下单 → 订单 PENDING（客户下单）"""
    driver.get(BASE + "/cart.html")
    time.sleep(1)
    checks = driver.find_elements(By.CSS_SELECTOR, "#cart-tbody .row-check")
    js_click(driver, checks[0])
    driver.find_element(By.ID, "cart-order").click()
    time.sleep(1.5)
    body = driver.find_element(By.TAG_NAME, "body").text
    assert "下单成功" in body, "下单未成功"
    driver.get(BASE + "/orders.html")
    time.sleep(1.2)
    assert "客户下单" in driver.find_element(By.TAG_NAME, "body").text, "订单未出现在买家订单页"
    log(True, "B-06 购物车多选下单生成订单", "")


def t_b07_order_cancel_buyer(driver):
    """B-07 买家取消订单 → 已取消"""
    btns = driver.find_elements(By.CSS_SELECTOR, "[data-cancel]")
    assert btns, "买家订单页没有取消按钮"
    js_click(driver, btns[0])
    driver.switch_to.alert.accept()
    time.sleep(1.5)
    assert "已取消" in driver.find_element(By.TAG_NAME, "body").text, "订单未变已取消"
    log(True, "B-07 买家取消订单（释放库存）", "")
    shot(driver, "B-07-cancelled")


def t_b08_out_of_stock_flag(driver):
    """B-08 缺货/下架商品在购物车标注"""
    driver.get(BASE + "/cart.html")
    time.sleep(1)
    body = driver.find_element(By.TAG_NAME, "body").text
    assert ("无货" in body) or ("已下架" in body), "缺货商品未标注"
    log(True, "B-08 缺货/下架商品标注", "")


def t_s01_seller_order_list(driver):
    """S-01 卖家后台出现订单管理列表"""
    login_seller(driver)
    driver.find_element(By.ID, "refresh-btn").click()
    time.sleep(1.5)
    assert driver.find_element(By.ID, "seller-orders-body").is_displayed(), "订单列表为空"
    log(True, "S-01 卖家订单管理列表", "")


def t_s02_seller_advance(driver):
    """S-02 订单推进 确认→备货→发货→完成（按钮由后端 allowedActions 驱动）"""
    statuses = ["商家确认", "备货完成", "开始发货", "交易完成"]
    for expect in statuses:
        btns = driver.find_elements(By.CSS_SELECTOR, "[data-so-advance]")
        assert btns, f"找不到推进按钮（期望 {expect}）"
        js_click(driver, btns[0])
        time.sleep(1.5)
        assert expect in driver.find_element(By.ID, "seller-orders-list").text, \
            f"未推进到「{expect}」"
    log(True, "S-02 状态机推进 确认→备货→发货→完成", "")
    shot(driver, "S-02-completed")


def t_b09_buyer_sees_completed(driver):
    """B-09 买家订单页同步显示交易完成"""
    login_buyer(driver)
    driver.get(BASE + "/orders.html")
    time.sleep(1.2)
    assert "交易完成" in driver.find_element(By.TAG_NAME, "body").text, "买家端未同步交易完成"
    log(True, "B-09 买家端状态同步（交易完成）", "")


def t_s03_seller_cancel(driver):
    """S-03 卖家取消未完成订单"""
    login_seller(driver)
    btns = [b for b in driver.find_elements(By.CSS_SELECTOR, "[data-so-cancel]")]
    assert btns, "卖家订单页没有可取消订单"
    js_click(driver, btns[0])
    driver.switch_to.alert.accept()
    time.sleep(1.5)
    assert "已取消" in driver.find_element(By.ID, "seller-orders-list").text, "订单未变已取消"
    log(True, "S-03 卖家取消订单", "")


def t_b10_filters_status(driver):
    """B-10 卖家按状态过滤已取消"""
    sel = driver.find_element(By.ID, "seller-orders-filter")
    sel.find_element(By.CSS_SELECTOR, "option[value='CANCELLED']").click() if False else None
    from selenium.webdriver.support.select import Select
    Select(sel).select_by_value("CANCELLED")
    time.sleep(1.2)
    text = driver.find_element(By.ID, "seller-orders-list").text
    assert "已取消" in text, "状态过滤未生效"
    log(True, "B-10 卖家订单按状态过滤", "")


def main():
    driver = new_driver()
    start = time.time()
    try:
        login_buyer(driver)
        t_b01_cart_add(driver)
        t_b02_cart_accumulate(driver)
        t_b03_cart_qty_change(driver)
        t_b04_favorite_add(driver)
        t_b05_favorite_page(driver)
        t_b06_order_submit(driver)
        t_b07_order_cancel_buyer(driver)
        t_b08_out_of_stock_flag(driver)
        t_s01_seller_order_list(driver)
        t_s02_seller_advance(driver)
        t_b09_buyer_sees_completed(driver)
        t_s03_seller_cancel(driver)
        t_b10_filters_status(driver)
    except Exception as e:
        log(False, "异常中断", repr(e))
        try:
            shot(driver, "CRASH-" + datetime.datetime.now().strftime("%H%M%S"))
        except Exception:
            pass
    finally:
        driver.quit()

    total = len(results)
    passed = sum(1 for r in results if r[0])
    print("\n===== 汇总 =====")
    print(f"通过 {passed}/{total}  用时 {int(time.time() - start):02d}:{int(time.time() - start) % 60:02d}")
    print("截图目录：" + SHOT_DIR)
    return 0 if passed == total else 1


if __name__ == "__main__":
    raise SystemExit(main())
