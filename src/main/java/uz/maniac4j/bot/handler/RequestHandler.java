package uz.maniac4j.bot.handler;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import uz.maniac4j.component.MessageTemplate;
import uz.maniac4j.model.*;
import uz.maniac4j.repository.TelegramUserRepository;
import uz.maniac4j.service.ItemService;
import uz.maniac4j.service.RequestService;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class RequestHandler implements Handler{

    private final MessageTemplate messageTemplate;

    private final RequestService requestService;
    private final ItemService itemService;
    private final ItemHandler itemHandler;
    private final TelegramUserRepository telegramUserRepository;

    public RequestHandler(MessageTemplate messageTemplate, RequestService requestService, ItemService itemService, ItemHandler itemHandler, TelegramUserRepository telegramUserRepository) {
        this.messageTemplate = messageTemplate;
        this.requestService = requestService;
        this.itemService = itemService;
        this.itemHandler = itemHandler;
        this.telegramUserRepository = telegramUserRepository;
    }

    @Override
    public List<PartialBotApiMethod<? extends Serializable>> handle(TelegramUser user, String message) throws IOException {

        String[] strings = message.split(":");
        System.out.println(Arrays.toString(strings));

        if (strings.length>=2){
            try {
                Integer.parseInt(strings[1].replace(" ",""));
            }catch (Exception e){
                return Collections.singletonList(messageTemplate.addItem(user,"Введите количество товаров правильно!\n"+user.getSection().getRu() + Translations.TXT_PRODUCT.getRu()));
            }
            Item item = Item.builder()
                    .section(user.getSection())
                    .name(strings[0])
                    .amount(Integer.parseInt(strings[1].replace(" ","")))
                    .description(strings.length == 3 ? strings[2] : "")
                    .build();
            item = itemService.add(user, item);
            return Collections.singletonList(messageTemplate.sectionSelect(user,false));
        }

        return null;
    }

    @Override
    public List<PartialBotApiMethod<? extends Serializable>> handle(TelegramUser user, CallbackQuery callback) throws IOException {
        String text=callback.getData();
        System.out.println("REQUEST HANDLER");

        Section section = checkSection(text);
        if (section!=null){
            user.setSection(section);
            user.setState(State.SECOND);
            user=telegramUserRepository.save(user);
            return Collections.singletonList(messageTemplate.addItem(user));
        }

        if (text.equals(Translations.SEND_REQUEST.name())){
            return Collections.singletonList(messageTemplate.confirmRequestMessage(user));
        }

        if (text.startsWith(Translations.DELETE_ITEM.name())) {
            String[] strings = text.split(":");
            itemService.delete(user, Long.valueOf(strings[1]));



            return Collections.singletonList(messageTemplate.editReplyMarkup(user,messageTemplate.sectionSelect(user),callback.getMessage().getMessageId()));
//            return Collections.singletonList(messageTemplate.sectionSelect(user));
        }


        if (text.equals(Translations.BACK_SECTION.name())) return Collections.singletonList(messageTemplate.sectionSelect(user,false));
        if (text.equals(Translations.DRAFT_REQUEST.name())) return Collections.singletonList(messageTemplate.sectionSelect(user,false));

        if (text.equals(Translations.CANCEL_REQUEST.name())||text.equals(Translations.BACK.name())) {
            return Collections.singletonList(messageTemplate.mainMenu(user));
//            return messageTemplate.mainMenuEdit(user,callback.getMessage().getMessageId());
        }

        if (text.equals(Translations.CONFIRM_REQUEST.name())) {
            Request confirm = requestService.confirm(user);
            List<PartialBotApiMethod<? extends Serializable>> list=new ArrayList<>();
            list.add(messageTemplate.confirm(user));
            list.add(messageTemplate.mainMenu(user));

            return list;
        }


        return null;
    }

    @Override
    public State operatedBotState() {
        return State.SECOND;
    }

    @Override
    public List<String> operatedCallBackQuery(TelegramUser user) {
        List<String> result=new ArrayList<>();

        for (Section section : Section.values()) {
            result.add(section.name());
        }
        result.add(Translations.SEND_REQUEST.name());
        result.add(Translations.BACK.name());
        result.add(Translations.BACK_SECTION.name());
        result.add(Translations.CONFIRM_REQUEST.name());
        result.add(Translations.CANCEL_REQUEST.name());
        result.add(Translations.DRAFT_REQUEST.name());


        Request last = requestService.getLast(user);
        if (last!=null){
            List<Item> items = itemService.allByRequest(last);
            for (Item item : items) {
                result.add(Translations.DELETE_ITEM.name()+":"+item.getId());
            }
        }


        return result;
    }

    public Section checkSection(String text){
        for (Section section : Section.values()) {
            if (section.name().equals(text))
                return section;
        }
        return null;
    }

}
