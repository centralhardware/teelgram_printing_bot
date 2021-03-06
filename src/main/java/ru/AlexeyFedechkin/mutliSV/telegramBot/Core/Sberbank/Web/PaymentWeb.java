package ru.AlexeyFedechkin.mutliSV.telegramBot.Core.Sberbank.Web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Core.Config;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Core.Cups.Cups;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Core.Cups.PrintDetail;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Core.Cups.PrintQue;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Core.Entity.Payment;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Core.Entity.UserType;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Core.Service.PaymentService;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Main;
import ru.AlexeyFedechkin.mutliSV.telegramBot.Telegram.TelegramBot;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * endpoint necessary to work with the payment system
 */
@RestController
@RequestMapping()
public class PaymentWeb {

    private static final Logger log = LoggerFactory.getLogger(PaymentWeb.class);

    private final PaymentService paymentService;
    private final Cups cups;
    private final PrintQue printQue;
    private TelegramBot telegramBot;
    private final List<String> alreadySend = new ArrayList<>();

    public PaymentWeb(PaymentService paymentService, Cups cups, PrintQue printQue) {
        this.paymentService = paymentService;
        this.cups = cups;
        this.printQue = printQue;
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ResponseEntity<?> index(){
        log.info("requesting index requesting");
        return ResponseEntity.ok(INDEX_PAGE);
    }

    /**
     * redirection to this endpoint will be performed if the payment was successful
     * @param orderId
     * @return
     */
    @RequestMapping(value = "/fail", method = RequestMethod.GET)
    public ResponseEntity<?> fail(@RequestParam String orderId) {
        if (telegramBot == null){
            telegramBot = Main.getTelegramBot();
        }
        log.info("fail page requesting");
        Optional<Payment> paymentOptional = paymentService.findByOrderId(orderId);
        if (paymentOptional.isPresent()){
            Payment payment = paymentOptional.get();
            payment.setIsSuccessfully(false);
            paymentService.save(payment);
            printQue.removeFromQue(String.valueOf(payment.getUuid()));
            if (payment.getUserType() == UserType.TELEGRAM){
                telegramBot.sendMessage("Оплата не удалась", payment.getCreatedByTelegram().getId());
            }
            return ResponseEntity.ok(FAIL_PAGE);
        } else {
            return ResponseEntity.ok(NO_TRANSACTION_PAGE);
        }
    }

    /**
     * redirection to this endpoint will be performed if the payment was successful
     * @param orderId
     * @return
     */
    @RequestMapping(value = "/success", method = RequestMethod.GET)
    public ResponseEntity<?> success(@RequestParam String orderId) {
        if (telegramBot == null){
            telegramBot = Main.getTelegramBot();
        }
        log.info("success page requesting");
        Optional<Payment> paymentOptional = paymentService.findByOrderId(orderId);
        if (paymentOptional.isPresent()){
            Payment payment = paymentOptional.get();
            payment.setIsSuccessfully(true);
            paymentService.save(payment);
            PrintDetail printDetail = null;
            if (payment.getUserType() == UserType.TELEGRAM){
                printDetail = printQue.getPrintDetail(String.valueOf(payment.getUuid()));
            }
            try {
                boolean result = cups.print(printDetail.getFile(), payment.getCreatedByTelegram().getUsername(), printDetail.getOriginalFileName());
                if (payment.getUserType() == UserType.TELEGRAM){
                    printQue.removeFromQue(String.valueOf(payment.getUuid()));
                }
                if (!result){
                    log.warn("print is not be successful");
                    if (payment.getUserType() == UserType.TELEGRAM){
                        if (!alreadySend.contains(payment.getUuid())){
                            telegramBot.sendMessage("Оплата произведена успешно, однако при печати документа произвошла ошибка", payment.getCreatedByTelegram().getId());
                            telegramBot.sendMessage(genInfoMessage("ошибка печати", printDetail, payment),
                                    Config.getTelegramGroupId(),
                                    printDetail.getFile(),
                                    printDetail.getOriginalFileName());
                            alreadySend.add(payment.getUuid());
                        }
                    }
                    return ResponseEntity.ok(SUCCESS_PAGE_PRINT_FAIL);
                }
            } catch (Exception e) {
                if (payment.getUserType() == UserType.TELEGRAM){
                    if (!alreadySend.contains(payment.getUuid())){
                        telegramBot.sendMessage("Оплата произведена успешно, однако при печати документа произвошла ошибка", payment.getCreatedByTelegram().getId());
                        alreadySend.add(payment.getUuid());
                    }
                }
                telegramBot.sendMessage(genInfoMessage("ошибка печати", printDetail, payment),
                        Config.getTelegramGroupId(),
                        printDetail.getFile(),
                        printDetail.getOriginalFileName());
                log.warn("unable to print document", e);
                return ResponseEntity.ok(SUCCESS_PAGE_PRINT_FAIL);
            }
            if (payment.getUserType() == UserType.TELEGRAM){
                if (!alreadySend.contains(payment.getUuid())){
                    telegramBot.sendMessage("Оплата произведена успешно.", payment.getCreatedByTelegram().getId());
                    telegramBot.sendMessage(genInfoMessage("успешно", printDetail, payment),
                            Config.getTelegramGroupId(),
                            printDetail.getFile(),
                            printDetail.getOriginalFileName());
                    alreadySend.add(payment.getUuid());
                }
            }
            return ResponseEntity.ok(SUCCESS_PAGE);
        } else {
            return ResponseEntity.ok(NO_TRANSACTION_PAGE);
        }
    }

    private static String genInfoMessage(String status, PrintDetail printDetail, Payment payment){
        return "Время " + new SimpleDateFormat("H:m").format(payment.getCreateDate()) + "\n" +
                "количество страниц - " + printDetail.getPrice() / Config.getPagePrice() + "\n" +
                "Пользователь: " + "\n" +
                "username: " +  payment.getCreatedByTelegram().getUsername() + "\n" +
                "first name " + payment.getCreatedByTelegram().getFirstName() + "\n" +
                "last name:  " + payment.getCreatedByTelegram().getSecondName() + "\n" +
                "Оплата: \n" +
                "UUID: " + payment.getUuid() + "\n" +
                "orderId: " + payment.getOrderId() + "\n" +
                "сумма платежа: " + payment.getAmount() + "\n" +
                "дата создания: " + new SimpleDateFormat("yyyy LLLL d E.- H:m", new Locale("ru")).format(payment.getCreateDate()) + "\n" +
                "статус печати: " + status;
    }

    private static final String NO_TRANSACTION_PAGE =
            "<center><h1>Транзакции с таким номером не существует</h1>" +
                    "<h2>Если вы нашли баг пожайлуста сообщите нам на printomat@centralhardware.ru</h2></center>" +
            "";

    private static final String FAIL_PAGE =
            "<center><H1>Произошла ошибка</H1>" +
            "<h3>Удостоверьтесь, что у вас достаточно средств на карте и повторите процесс</h3>" +
            "</center>";

    private static final String SUCCESS_PAGE_PRINT_FAIL =
            "<center> <H1>Оплата прошла успешно. Однако печать не удалась</H1> " +
            "<H3>Приносим свои извенения. Во время отправки на печать произвошла ошибка</H3>" +
            "</center>" +
            "<center>" + Config.getEmbeddedMapIframe() + "</center>";

    private static final String SUCCESS_PAGE =
            "<center> <H1>Оплата прошла успешно</H1> " +
            "<H3>Благодарим за покупку в " + Config.getCompanyName() + "</H3>" +
            "<H3>Документ уже отправлен на печать. Вы можете забрать его по адресу: "+ Config.getCompanyLocation() + "</H3>" +
            "</center>" +
            "<center>" + Config.getEmbeddedMapIframe() + "</center>";

    private static final String INDEX_PAGE =
            "<center><h1>printomat</h1>" +
                    "<h2>Вы попали на служебный домен payment.printomat.online</h2>" +
                    "<h2>Он используется для работы с платежной системой и не предназначен для посещения </h2>" +
                    "</center>";

}