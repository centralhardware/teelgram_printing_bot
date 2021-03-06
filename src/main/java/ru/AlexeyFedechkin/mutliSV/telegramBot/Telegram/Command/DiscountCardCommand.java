package ru.AlexeyFedechkin.mutliSV.telegramBot.Telegram.Command;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.extensions.bots.commandbot.commands.BotCommand;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Core.QR;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Core.Service.UserService;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Core.SpringContext;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Telegram.Mapper;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Telegram.TelegramCache;

import java.util.Comparator;

/**
 * get QR code with unique user token
 */
public class DiscountCardCommand extends BotCommand {

    private static final Logger log = LoggerFactory.getLogger(DiscountCardCommand.class);

    public DiscountCardCommand() {
        super("discount_card", "get discount qr");
        service = SpringContext.getBean(UserService.class);
        telegramCache = SpringContext.getBean(TelegramCache.class);
    }

    private final UserService service;
    private final TelegramCache telegramCache;

    @Override
    public void execute(AbsSender absSender, User user, Chat chat, String[] strings) {
        log.info(String.format("execute command %s for user %s", DiscountCardCommand.class.toString(), user.getUserName()));
        service.create(Mapper.toInnerUser(user));
        try {
            SendChatAction sendChatAction = new SendChatAction().setChatId(chat.getId()).setAction(ActionType.UPLOADPHOTO);
            absSender.execute(sendChatAction);
            SendPhoto sendPhoto = new SendPhoto().setChatId(chat.getId());
            if (telegramCache.get(user.getId()) != null){
                sendPhoto.setPhoto(telegramCache.get(user.getId()));
                absSender.execute(sendPhoto);
                log.info(String.format("send photo by file id- %s to user %s",telegramCache.get(user.getId()), user.getUserName() ));
            } else {
                log.info(String.format("send photo by InputStream for user %s", user.getUserName()));
                sendAndStoreCache(sendPhoto,user,absSender);
            }
        } catch (TelegramApiException e) {
            log.warn("unable to send message", e);
        }
    }

    private void sendAndStoreCache(@NonNull SendPhoto sendPhoto, @NonNull User user, @NonNull AbsSender absSender){
        try {
            sendPhoto.setPhoto(String.format("QR for %s", user.getUserName()), QR.generateQRCodeImage(service.getToken(Long.valueOf(user.getId()))));
            var message = absSender.execute(sendPhoto);
            String fileId = message.getPhoto().stream().max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null).getFileId();
            telegramCache.store(user.getId(), fileId);
        } catch (TelegramApiException e) {
            log.warn("unable to send message with cache", e);
        }
    }
}
