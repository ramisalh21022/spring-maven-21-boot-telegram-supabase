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
    private final Long distributorChatId = 963933210196L;

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
            clientsCache.put(chatId, (Integer) client.get("id"));
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
                    .callbackData("order_" + product.get("id"))
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
            Integer clientId = clientsCache.get(chatId);
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
    }

    private void handleContact(Long chatId, Contact contact) throws TelegramApiException {
        Integer orderId = pendingOrders.get(chatId);
        Map<String, Object> client = clientsDataCache.get(chatId);
        if (orderId == null || client == null) return;

        supabaseService.updateClientPhone((Integer) client.get("id"), contact.getPhoneNumber());
        supabaseService.confirmOrder(orderId);

        execute(SendMessage.builder().chatId(chatId.toString())
                .text(String.format("✅ تم تأكيد طلبك بنجاح.\n🎉 رقم الطلب: %d\n👤 %s\n📱 هاتفك: %s\n🚚 سيتم التواصل معك قريبًا.",
                        orderId, client.get("owner_name"), contact.getPhoneNumber()))
                .build());

        execute(SendMessage.builder().chatId(distributorChatId.toString())
                .text(String.format("📦 طلب جديد مؤكد!\n🎉 رقم الطلب: %d\n👤 العميل: %s\n📱 الهاتف: %s",
                        orderId, client.get("owner_name"), contact.getPhoneNumber()))
                .build());

        pendingOrders.remove(chatId);
    }
}
