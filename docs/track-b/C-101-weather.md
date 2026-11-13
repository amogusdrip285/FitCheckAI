# C-101 — Thời tiết theo thành phố (Track B)

> Owner: B · 13/11/2026. Code: `core/ai/.../weather/CityWeatherResolver.kt` (Hilt `@Singleton`, `ProxyModule`). Consumer: D (Today, profile).

## Nguồn và điều khoản

- **Open-Meteo** forecast API (`api.open-meteo.com/v1/forecast`, tham số `current=temperature_2m,weather_code`). Không cần key; app gọi thẳng (không qua proxy).
- Điều khoản đọc ngày 13/11/2026 ([terms](https://open-meteo.com/en/terms), [licence](https://open-meteo.com/en/licence)):
  - Free API **chỉ cho mục đích phi thương mại** (không quảng cáo, không thu phí). Sản phẩm có quảng cáo/subscription phải mua gói trả phí — nhóm cần xem lại nếu thương mại hoá.
  - Giới hạn free: < 10 000 call/ngày, 5 000/giờ, 600/phút (theo IP). Cache 30 phút ⇒ mỗi máy ≤ ~48 call/ngày/thành phố.
  - Dữ liệu CC BY 4.0: **bắt buộc** hiển thị link ghi nguồn cạnh chỗ hiện thời tiết: `Weather data by Open-Meteo.com` → `https://open-meteo.com/` (hằng `WEATHER_ATTRIBUTION_TEXT` / `WEATHER_ATTRIBUTION_URL`).
- Dữ liệu gửi đi: chỉ toạ độ trung tâm thành phố + tham số kỹ thuật. Không GPS, không ID/thông tin người dùng.

## Hành vi

| Tình huống | Kết quả `weatherFor(cityKey)` |
|---|---|
| Chưa chọn thành phố / không thuộc danh sách | `null`, **không gọi mạng** (không đoán vị trí) |
| Thành công | `WeatherSnapshot(cityLabel, tempC, condition tiếng Việt, validAt, fetchedAt, source = "Open-Meteo", isStale)` |
| Gọi lại trong 30 phút | trả cache, không gọi mạng |
| Số đo cũ hơn 1 giờ | `isStale = true` |
| Timeout 3s / mất mạng / dữ liệu hỏng | cache ≤ 6 giờ với `isStale = true`, không có thì `null` |
| Caller huỷ coroutine | ném `CancellationException` như bình thường |

Không bao giờ ném lỗi khác ⇒ thời tiết **không chặn** phối đồ: request vẫn gửi với `weather = null`, và prompt `outfits` đã có luật "null ⇒ không nhắc nhiệt độ; isStale ⇒ nói rõ là dữ liệu cũ".

Thành phố hỗ trợ (`WeatherCities.ALL`): Hà Nội, TP. Hồ Chí Minh, Đà Nẵng, Hải Phòng, Cần Thơ, Huế, Nha Trang, Đà Lạt, Vũng Tàu, Hạ Long, Vinh, Quy Nhơn. Khớp không dấu và alias (`"Hà Nội"`, `"ha noi"`, `"HN"`, `"TP.HCM"`, `"Sài Gòn"`…).

## Việc của D (Today / profile)

1. Profile: nên cho chọn thành phố từ `WeatherCities.ALL` và lưu `cityKey = city.key` (tên tự gõ vẫn khớp nếu đúng một thành phố trong danh sách).
2. Today: `val weather = cityWeatherResolver.weatherFor(profile?.cityKey)` rồi đặt vào `OutfitRequest(weather = weather)`. Không chờ thời tiết quá 3s; lỗi ⇒ vẫn gửi request.
3. Hiển thị: `cityLabel`, `tempC`, `condition`, "cập nhật lúc …" từ `validAt`; nếu `isStale` ghi rõ "dữ liệu cũ", không hiển thị như hiện tại; luôn kèm link `WEATHER_ATTRIBUTION_TEXT`.
4. `weather = null` ⇒ ghi "Chưa có dữ liệu thời tiết", không bịa số.

## Kiểm thử

`core/ai` `CityWeatherResolverTest` (MockEngine, không mạng): mapping, thành phố lạ/không chọn không gọi mạng, alias, cache 30 phút, refresh lỗi ⇒ cache stale / quá 6 giờ ⇒ null, số đo cũ ⇒ stale, timeout/mất mạng/JSON hỏng/nhiệt độ vô lý ⇒ null, mã WMO lạ, huỷ coroutine. Gọi thật một lần ngày 13/11 (Hà Nội): 200 trong 0,9s, đúng định dạng `current.time` (unix giây), `temperature_2m`, `weather_code`. Chưa chạy trên máy thật offline (C-101 testing) — cần D nối UI trước.
