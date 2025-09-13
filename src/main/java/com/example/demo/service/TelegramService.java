package com.example.demo.service;

import com.example.demo.config.AppConfig;
import com.example.demo.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TelegramService extends TelegramWebhookBot {

    private final AppConfig appConfig;
    private final SupabaseService supabaseService;

    private final Map<Long, Integer> clientsCache = new HashMap<>();
    private final Map<Long, Map<String, Object>> clientsDataCache = new HashMap<>();
    private final Map<Long, Integer> pendingOrders = new HashMap<>();
    private final Long distributorChatId = 963940452940L;

    @Override
    public String getBotUsername() {
        return System.getenv("TELEGRAM_USERNAME");
    }

    @Override
    public String getBotToken() {
        return System.getenv("TELEGRAM_TOKEN");
    }

    @Override
    public String getBotPath() {
        // هذا المسار هو endpoint للـ webhook
        return "/webhook/" + getBotToken();
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) handleMessage(update.getMessage());
            else if (update.hasCallbackQuery()) handleCallback(update.getCallbackQuery());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // ✅ الآن متوافق مع التوقيع
    }


    private void handleMessage(Message msg) throws TelegramApiException {
        Long chatId = msg.getChatId();

        if (msg.hasContact()) {
            handleContact(chatId, msg.getContact());
            return;
        }

        String keyword = msg.getText();
        if (keyword == null || keyword.isBlank()) {
            execute(SendMessage.builder().chatId(chatId.toString())
                    .text("أرسل كلمة للبحث 🔍 مثال: سكر").build());
            return;
        }

        Map<String, Object> client = clientsDataCache.get(chatId);
        if (client == null) {
            client = supabaseService.createOrGetClient(msg.getFrom(), chatId);
            clientsDataCache.put(chatId, client);
            // التحويل الآمن للـ Integer
            Number idNumber = (Number) client.get("id");
            Integer clientId = idNumber.intValue();
            clientsCache.put(chatId, clientId);
        }

        execute(SendMessage.builder().chatId(chatId.toString())
                .text("👋 أهلا " + client.get("owner_name") + "، مرحبًا بك في متجرنا!").build());

        List<Map<String, Object>> products = supabaseService.searchProducts(keyword);
        if (products.isEmpty()) {
            execute(SendMessage.builder().chatId(chatId.toString())
                    .text("🚫 لا يوجد نتائج لكلمة: " + keyword).build());
            return;
        }

        for (Map<String, Object> product : products) {
            String caption = String.format("🛒 *%s*\n📦 %s\n💵 %s ل.س",
                    product.get("product_name"), product.get("category"), product.get("price"));

            InlineKeyboardButton orderBtn = InlineKeyboardButton.builder()
                    .text("اطلب الآن")
                    .callbackData("order_" + ((Number) product.get("id")).intValue()) // آمن
                    .build();
            List<InlineKeyboardButton> row = Collections.singletonList(orderBtn);
            List<List<InlineKeyboardButton>> keyboard = Collections.singletonList(row);
            InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder().keyboard(keyboard).build();

            if (product.get("image_url") != null) {
                SendPhoto photo = new SendPhoto();
                photo.setChatId(chatId.toString());
                photo.setPhoto(new InputFile((String) product.get("image_url")));
                photo.setCaption(caption);
                photo.setParseMode("Markdown");
                photo.setReplyMarkup(markup);
                execute(photo);
            } else {
                execute(SendMessage.builder().chatId(chatId.toString())
                        .text(caption)
                        .parseMode("Markdown")
                        .replyMarkup(markup)
                        .build());
            }
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) throws TelegramApiException {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();

        if (data.startsWith("order_")) {
            Integer productId = Integer.parseInt(data.split("_")[1]);
            Integer clientId = null;
            Number idNumber = clientsCache.get(chatId);
            if (idNumber != null) {
                clientId = idNumber.intValue();
            }
            if (clientId == null) {
                execute(SendMessage.builder().chatId(chatId.toString())
                        .text("⚠️ حدث خطأ: العميل غير موجود").build());
                return;
            }

            Integer orderId = supabaseService.initOrder(clientId);
            supabaseService.addOrderItem(orderId, productId, 1);
            pendingOrders.put(chatId, orderId);

            KeyboardButton contactBtn = KeyboardButton.builder().text("📲 مشاركة رقمي").requestContact(true).build();
            KeyboardRow row = new KeyboardRow();
            row.add(contactBtn);
            ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                    .keyboard(Collections.singletonList(row))
                    .resizeKeyboard(true)
                    .oneTimeKeyboard(true)
                    .build();

            execute(SendMessage.builder().chatId(chatId.toString())
                    .text("📱 يرجى تأكيد طلبك بمشاركة رقم هاتفك:")
                    .replyMarkup(keyboard)
                    .build());

            execute(new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery(callbackQuery.getId()));
        }
        if (data.startsWith("SEND_TG")) {
            execute(SendMessage.builder().chatId(chatId.toString())
                    .text("📢 تم إرسال طلبك للمتجر عبر Telegram ✅").build());

            execute(SendMessage.builder().chatId(distributorChatId.toString())
                    .text("📦 طلب جديد من Telegram\n🆔 رقم الطلب: " + data.split(":")[1])
                    .build());
        }

        else if (data.startsWith("SEND_WA")) {
            execute(SendMessage.builder().chatId(chatId.toString())
                    .text("💬 تم تجهيز رابط لإرسال الطلب عبر WhatsApp ✅").build());

            // رابط جاهز لواتساب
            String waLink = "https://wa.me/" + distributorChatId +
                    "?text=طلب%20جديد%20رقم%20" + data.split(":")[1];
            execute(SendMessage.builder().chatId(chatId.toString())
                    .text("اضغط هنا لإرسال الطلب عبر WhatsApp:\n" + waLink).build());
        }

        else if (data.startsWith("SEND_SMS")) {
            execute(SendMessage.builder().chatId(chatId.toString())
                    .text("📩 تم تجهيز رسالة SMS لإرسال الطلب ✅").build());

            String smsLink = "sms:" + distributorChatId + "?body=طلب جديد رقم " + data.split(":")[1];
            execute(SendMessage.builder().chatId(chatId.toString())
                    .text("اضغط هنا لإرسال SMS:\n" + smsLink).build());
        }


    }

   private void handleContact(Long chatId, Contact contact) throws TelegramApiException {
        Integer orderId = pendingOrders.get(chatId);
        Map<String, Object> client = clientsDataCache.get(chatId);
        if (orderId == null || client == null) return;

        // تحديث الرقم باستخدام الدالة الجديدة
        Integer finalClientId = supabaseService.updateClientPhone(
                (Integer) client.get("id"),
                contact.getPhoneNumber(),
                (String) client.get("owner_name")
        );

        if (finalClientId == null) {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("⚠️ حدث خطأ أثناء تحديث رقم الهاتف، يرجى المحاولة لاحقاً.")
                    .build());
            return;
        }

        // تحديث الكاش بالـ clientId النهائي
        clientsCache.put(chatId, finalClientId);

        // تأكيد الطلب
        supabaseService.confirmOrderAndGet(orderId);

        // بطاقة ترحيب
        String distributorPhone = "963940452940"; // رقم المتجر الثابت
        String message = "🎉 شكراً لتأكيد طلبك!\n\n" +
                "🆔 رقم الطلب: " + orderId + "\n" +
                "👤 الاسم: " + client.get("owner_name") + "\n" +
                "📱 هاتفك: " + contact.getPhoneNumber() + "\n" +
                "☎️ هاتف المتجر: " + distributorPhone + "\n\n" +
                "✅ اختر طريقة إرسال طلبك للمتجر:";

        InlineKeyboardButton tgButton = InlineKeyboardButton.builder()
                .text("📢 عبر Telegram")
                .callbackData("SEND_TG:" + orderId)
                .build();

        InlineKeyboardButton waButton = InlineKeyboardButton.builder()
                .text("💬 عبر WhatsApp")
                .callbackData("SEND_WA:" + orderId)
                .build();

        InlineKeyboardButton smsButton = InlineKeyboardButton.builder()
                .text("📩 عبر SMS")
                .callbackData("SEND_SMS:" + orderId)
                .build();

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        List.of(tgButton),
                        List.of(waButton),
                        List.of(smsButton)
                ))
                .build();

        execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(message)
                .replyMarkup(markup)
                .build());

        // تنظيف الطلبات المعلقة
        pendingOrders.remove(chatId);
    }
}



