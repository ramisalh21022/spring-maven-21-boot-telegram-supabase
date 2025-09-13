package com.example.demo.service;

import com.example.demo.config.AppConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.ParameterizedTypeReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupabaseService {

    private final AppConfig appConfig;
    private final WebClient webClient = WebClient.builder().build();

    // إنشاء أو استرجاع العميل
    public Map<String, Object> createOrGetClient(org.telegram.telegrambots.meta.api.objects.User user, Long chatId) {
        String phone = user.getUserName() != null ? "@" + user.getUserName() : "tg_" + chatId;
        String ownerName = (user.getFirstName() != null ? user.getFirstName() : "") +
                (user.getLastName() != null ? " " + user.getLastName() : "");
        ownerName = ownerName.isBlank() ? "غير معروف" : ownerName;
        String storeName = "عميل_" + chatId;

        // تحقق إذا العميل موجود
        List<Map<String, Object>> existing = webClient.get()
                .uri(appConfig.getSupabaseUrl() + "/rest/v1/clients?phone=eq." + phone)
                .header("apikey", appConfig.getSupabaseKey())
                .header("Authorization", "Bearer " + appConfig.getSupabaseKey())
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (existing != null && !existing.isEmpty()) return existing.get(0);

        // إنشاء جديد
        Map<String, Object> client = new HashMap<>();
        client.put("phone", phone);
        client.put("owner_name", ownerName);
        client.put("store_name", storeName);
        client.put("address", null);

        List<Map<String, Object>> response = webClient.post()
                .uri(appConfig.getSupabaseUrl() + "/rest/v1/clients")
                .header("apikey", appConfig.getSupabaseKey())
                .header("Authorization", "Bearer " + appConfig.getSupabaseKey())
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .bodyValue(client)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        return response.get(0);
    }

    // البحث عن المنتجات
    public List<Map<String, Object>> searchProducts(String keyword) {
        String filter = "product_name=ilike.*" + keyword + "*";
        List<Map<String, Object>> products = webClient.get()
                .uri(appConfig.getSupabaseUrl() + "/rest/v1/products_comp?" + filter + "&order=created_at.desc")
                .header("apikey", appConfig.getSupabaseKey())
                .header("Authorization", "Bearer " + appConfig.getSupabaseKey())
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (products != null) {
            for (Map<String, Object> p : products) {
                if (p.get("image_url") != null) {
                    p.put("image_url", appConfig.getBucketUrl() + p.get("image_url"));
                }
            }
        }

        return products;
    }

    // إنشاء طلب
    public Integer initOrder(Integer clientId) {
        // تحقق إذا يوجد طلب مفتوح
        List<Map<String, Object>> existing = webClient.get()
                .uri(appConfig.getSupabaseUrl() + "/rest/v1/orders?client_id=eq." + clientId + "&status=eq.pending")
                .header("apikey", appConfig.getSupabaseKey())
                .header("Authorization", "Bearer " + appConfig.getSupabaseKey())
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (existing != null && !existing.isEmpty()) return (Integer) existing.get(0).get("id");

        // إنشاء جديد
        Map<String, Object> order = new HashMap<>();
        order.put("client_id", clientId);
        order.put("status", "pending");
        order.put("total_price", 0);

        List<Map<String, Object>> response = webClient.post()
                .uri(appConfig.getSupabaseUrl() + "/rest/v1/orders")
                .header("apikey", appConfig.getSupabaseKey())
                .header("Authorization", "Bearer " + appConfig.getSupabaseKey())
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .bodyValue(order)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        return (Integer) response.get(0).get("id");
    }

    // إضافة عنصر للطلب
public boolean addOrderItem(Integer orderId, Integer productId, Integer quantity) {
    try {
        // جلب المنتج أولاً
        List<Map<String, Object>> prodList = webClient.get()
                .uri(appConfig.getSupabaseUrl() + "/rest/v1/products_comp?id=eq." + productId)
                .header("apikey", appConfig.getSupabaseKey())
                .header("Authorization", "Bearer " + appConfig.getSupabaseKey())
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (prodList == null || prodList.isEmpty()) {
            System.out.println("المنتج غير موجود: " + productId);
            return false;
        }

        Map<String, Object> prod = prodList.get(0);
        Number priceNum = (Number) prod.get("price"); // التعامل مع أي نوع رقمي
        int price = priceNum != null ? priceNum.intValue() : 0;

        // إنشاء عنصر الطلب
        Map<String, Object> item = new HashMap<>();
        item.put("order_id", orderId);
        item.put("product_id", productId);
        item.put("quantity", quantity);
        item.put("unit_price", price);

        // إرسال الطلب إلى Supabase
        List<Map<String,Object>> response = webClient.post()
                .uri(appConfig.getSupabaseUrl() + "/rest/v1/order_items")
                .header("apikey", appConfig.getSupabaseKey())
                .header("Authorization", "Bearer " + appConfig.getSupabaseKey())
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .bodyValue(item)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (response == null || response.isEmpty()) {
            System.out.println("فشل إضافة عنصر للطلب: " + productId);
            return false;
        }

        // تحديث إجمالي الطلب
        List<Map<String,Object>> totalItems = webClient.get()
                .uri(appConfig.getSupabaseUrl() + "/rest/v1/order_items?order_id=eq." + orderId)
                .header("apikey", appConfig.getSupabaseKey())
                .header("Authorization", "Bearer " + appConfig.getSupabaseKey())
                .retrieve()
                .bodyToMono(List.class)
                .block();

        int totalPrice = 0;
        if (totalItems != null) {
            totalPrice = totalItems.stream()
                    .mapToInt(i -> {
                        Number q = (Number) i.get("quantity");
                        Number p = (Number) i.get("unit_price");
                        return q.intValue() * p.intValue();
                    })
                    .sum();
        }

        Map<String, Object> totalUpdate = new HashMap<>();
        totalUpdate.put("total_price", totalPrice);

        webClient.patch()
                .uri(appConfig.getSupabaseUrl() + "/rest/v1/orders?id=eq." + orderId)
                .header("apikey", appConfig.getSupabaseKey())
                .header("Authorization", "Bearer " + appConfig.getSupabaseKey())
                .header("Content-Type", "application/json")
                .bodyValue(totalUpdate)
                .retrieve()
                .bodyToMono(Void.class)
                .block();

        return true; // تم الإضافة بنجاح

    } catch (Exception e) {
        e.printStackTrace();
        return false;
    }
}




    // تحديث رقم الهاتف للعميل
public Integer updateClientPhone(Integer clientId, String phone) {
    try {
        // محاولة تحديث العميل الحالي
        Map<String,Object> body = new HashMap<>();
        body.put("phone", phone);

        webClient.patch()
                .uri(appConfig.getSupabaseUrl() + "/rest/v1/clients?id=eq." + clientId)
                .header("apikey", appConfig.getSupabaseKey())
                .header("Authorization", "Bearer " + appConfig.getSupabaseKey())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .block();

        return clientId; // تم التحديث بنجاح

    } catch (WebClientResponseException.Conflict e) {
        // حصل تعارض بسبب رقم الهاتف موجود مسبقًا
        // البحث عن العميل حسب الرقم وإرجاع الـ clientId الموجود
        List<Map<String, Object>> existingClients = webClient.get()
                .uri(appConfig.getSupabaseUrl() + "/rest/v1/clients?phone=eq." + phone)
                .header("apikey", appConfig.getSupabaseKey())
                .header("Authorization", "Bearer " + appConfig.getSupabaseKey())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String,Object>>>() {})
                .block();

        if (existingClients != null && !existingClients.isEmpty()) {
            return ((Number) existingClients.get(0).get("id")).intValue();
        } else {
            // لو الرقم غير موجود، نعيد clientId القديم
            return clientId;
        }
    } catch (Exception e) {
        e.printStackTrace();
        return null; // فشل التحديث
    }
}

    // تأكيد الطلب
   // تأكيد الطلب وإرجاع معلوماته
// تأكيد الطلب وإرجاع معلوماته
public Map<String, Object> confirmOrderAndGet(Integer orderId) {
    Map<String,Object> body = new HashMap<>();
    body.put("status", "confirmed");

    // تحديث الطلب
    List<Map<String,Object>> updated = webClient.patch()
            .uri(appConfig.getSupabaseUrl() + "/rest/v1/orders?id=eq." + orderId)
            .header("apikey", appConfig.getSupabaseKey())
            .header("Authorization", "Bearer " + appConfig.getSupabaseKey())
            .header("Content-Type", "application/json")
            .header("Prefer", "return=representation")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(List.class)
            .block();

    if (updated == null || updated.isEmpty()) {
        throw new RuntimeException("لم يتم العثور على الطلب " + orderId);
    }

    return updated.get(0);
}


}









