import org.apache.commons.io.FileUtils;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;


public class MyBot extends TelegramLongPollingBot {

    int currentBright = 1;
    private final static String[] prevNext = new String[] {"back","next"};


    private static InlineKeyboardMarkup mainKeyboard = new InlineKeyboardMarkup();

    static{
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (String topic : prevNext) {
            row.add(InlineKeyboardButton.builder().text(topic).callbackData(topic).build());
            //keyboardRows.add(List.of(InlineKeyboardButton.builder().text(topic).callbackData(topic).build()));
        }
        keyboardRows.add(row);
        mainKeyboard.setKeyboard(keyboardRows);
    }

    static {System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        System.out.println("Version: " + Core.VERSION);}

    static Long id;


    public MyBot(String botToken) {
        super(botToken);
    }

    public void sendText(Long who, String what){
        SendMessage sm  = SendMessage.builder()
                .chatId(who.toString())
                .text(what)
                .build();
        try{
            execute(sm);
        }
        catch(TelegramApiException e){
            throw new RuntimeException(e);
        }
    }



    public static void main(String[] args) throws TelegramApiException, IOException {
        FileUtils.cleanDirectory(new java.io.File("photos"));
        TelegramBotsApi botApi = new TelegramBotsApi(DefaultBotSession.class);
        MyBot bot = new MyBot("6297786814:AAGkkCFFvPOLsCDg5wa_iG08wdg5c0AwlYA");
        botApi.registerBot(bot);
    }


    int photoNumber = 0;

    private void buttonTap(String chatId, String queryId, String data, int msgId) throws FileNotFoundException {

        EditMessageMedia editMessageMedia = EditMessageMedia.builder()
                .chatId(chatId)
                .messageId(msgId)
                .media(new InputMediaPhoto())
                .build();

        EditMessageReplyMarkup newKb = EditMessageReplyMarkup.builder()
                .chatId(chatId)
                .messageId(msgId)
                .build();

        // Загружаем изображение, храним его как объект матрицы.
        Mat image = Imgcodecs.imread("photos/photo_"+ photoNumber +".png");

        /* Создаём пустое изображение для записи в него
           нового обработаного изображения. */
        Mat newImage = new Mat(image.size(), image.type());

        double alpha = 1;
        double beta = 1;
        String newPhotoPath = "";

        switch (data) {
            case "next":
                if(photoNumber >= 0) {
                    beta = 25;
                    changeBrightness(image, newImage, alpha, beta);
                    // Сохраняем изображение.
                    newPhotoPath = "photos/photo_"+ ++photoNumber +".png";
                    Imgcodecs.imwrite(newPhotoPath, newImage);
                }
                else {
                    newPhotoPath = "photos/photo_"+ ++photoNumber +".png";
                }
                break;
            case "back":
                if(photoNumber <= 0) {
                    beta = -25;
                    changeBrightness(image, newImage, alpha, beta);
                    // Сохраняем изображение.
                    newPhotoPath = "photos/photo_"+ --photoNumber +".png";
                    Imgcodecs.imwrite(newPhotoPath, newImage);
                }
                else {
                    newPhotoPath = "photos/photo_"+ --photoNumber +".png";
                }
                break;
        }

        java.io.File newPhoto = new java.io.File(newPhotoPath);
        InputMediaPhoto media = new InputMediaPhoto();

        media.setMedia(new FileInputStream(newPhoto), newPhoto.getName());
        editMessageMedia.setMedia(media);
        newKb.setReplyMarkup(mainKeyboard);

        AnswerCallbackQuery close = AnswerCallbackQuery.builder()
                .callbackQueryId(queryId).build();
        try {
            execute(close);
            execute(editMessageMedia);
            execute(newKb);
        }
        catch(TelegramApiException e){
            throw new RuntimeException(e);
        }
    }

    void sendMenu(Long who, String txt, InlineKeyboardMarkup kb){

        SendMessage sm  = SendMessage.builder()
                .chatId(who.toString())
                .replyMarkup(kb)
                .parseMode("HTML")
                .text(txt)
                .build();
        try{
            execute(sm);
        }
        catch(TelegramApiException e){
            throw new RuntimeException(e);
        }
    }


    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            Message message = callbackQuery.getMessage();

            // обрабатываем CallbackQuery
            Integer messageId = message.getMessageId();
            try {
                buttonTap(message.getChatId().toString(), callbackQuery.getId(), callbackQuery.getData(), messageId);
            }
            catch(FileNotFoundException e){
                e.printStackTrace();
            }
        }
        if (update.hasMessage()) {
            var msg = update.getMessage();
            var user = msg.getFrom();
            id = user.getId();

            if (msg.isCommand()) {
                if (msg.getText().equals("/hello"))
                    sendText(id, "Привет, я могу обработать изображение, скинь картинку и я скину картинку тебе -> ");
            }
            if (msg.hasPhoto()) {
                photoNumber = 0;
                List<PhotoSize> photos = update.getMessage().getPhoto();

                // We fetch the bigger photo

                PhotoSize photo = photos.stream().max(Comparator.comparing(PhotoSize::getFileSize)).orElse(null);
                GetFile getFile = new GetFile(photo.getFileId());
                try {
                    File file = MyBot.this.execute(getFile); //tg file obj
                    MyBot.this.downloadFile(file, new java.io.File("photos/photo_"+photoNumber+".png"));
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }

                sendText(id, "Листайте для изменения яркости картинки");

                try {
                    SendPhoto sendPhotoRequest = new SendPhoto();
                    // Set destination chat id
                    sendPhotoRequest.setChatId(msg.getChatId());
                    // Set the photo file as a new photo (You can also use InputStream with a constructor overload)
                    sendPhotoRequest.setPhoto(new InputFile(new java.io.File("photos/photo_"+photoNumber+".png")));
                    sendPhotoRequest.setReplyMarkup(mainKeyboard);
                    execute(sendPhotoRequest);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    private void changeBrightness(Mat image, Mat newImage,double alpha, double beta){
        byte[] imageData = new byte[(int) (image.total() * image.channels())];
        image.get(0, 0, imageData);
        byte[] newImageData = new byte[(int) (newImage.total() * newImage.channels())];
        for (int y = 0; y < image.rows(); y++) {
            for (int x = 0; x < image.cols(); x++) {
                for (int c = 0; c < image.channels(); c++) {
                    double pixelValue = imageData[(y * image.cols() + x) * image.channels() + c];
                    pixelValue = pixelValue < 0 ? pixelValue + 256 : pixelValue;
                    newImageData[(y * image.cols() + x) * image.channels() + c]
                            = saturate(alpha * pixelValue + beta);
                }
            }
        }
        newImage.put(0, 0, newImageData);
    }

    private byte saturate(double val) {
        int iVal = (int) Math.round(val);
        iVal = iVal > 255 ? 255 : (iVal < 0 ? 0 : iVal);
        return (byte) iVal;
    }

@Override
public String getBotUsername() {
        return "@stingyDaniPhotoBot";
    }
}

