package com.neusoft.colockin;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

public class ColockInManager {

    //双休日是否需要打卡
    private static final boolean NEED_C0LOCKIN_FOR_WEEKENDS = false;
    //打卡失败重试次数
    private static final int RETRY_NUM = 3;
    //日期过滤
    private static final String[] DATE_FILETER = {"星期六", "星期日"};
    private static final String CHECK_CODE_PATH = "D:/ColockInAssistant/temp/";
    private static final String CHECK_CODE_NAME = "checkCode.jpg";
    private static final String OCR_CHECK_CODE_NAME = "ocrCheckCodeo.jpg";
    private Timer clockInWorkOnTimer;
    private Timer clockInWorkOffTimer;
    private boolean isFirstRun;

    public ColockInManager() {
        clockInWorkOnTimer = new Timer();
        clockInWorkOffTimer = new Timer();
        isFirstRun = true;
    }

    //执行打卡任务
    public void executeColockInTask() {
        executeWorkOnColockInTask();
        executeWorkOffColockInTask();
        isFirstRun = false;
        List<User> userList = getUserList();
        for (User user : userList) {
            print(user.getAlias());
        }
    }

    //执行上班打卡任务
    private void executeWorkOnColockInTask() {
        Date clockInWorkOnTime = getWorkOnClockInTime(8, 40, 5);
        print("WorkOnDate:" + clockInWorkOnTime);
        clockInWorkOnTimer.schedule(new ClockInWorkOnTask(), clockInWorkOnTime);
    }

    //执行下班打卡任务
    private void executeWorkOffColockInTask() {
        Date clockInWorkOffTime = getWorkOnClockInTime(17, 40, 0);
        print("WorkOffDate:" + clockInWorkOffTime);
        clockInWorkOffTimer.schedule(new ClockInWorkOffTask(), clockInWorkOffTime);
    }

    //获取上班下班打卡时间(amplitudeNum:分钟波动幅度)
    private Date getWorkOnClockInTime(int baseHour, int baseMinute, int amplitudeMinute) {
        if (amplitudeMinute > 30) {
            amplitudeMinute = 30;
        }
        int hour = baseHour;
        int minute = baseMinute;
        int second = getRandomMill(60, false);
        int randomNum = getRandomMill(amplitudeMinute, true); //取10以内的随即正负数
        minute = minute + randomNum;
        if (minute < 0) {
            hour = hour - 1;
            minute = 60 + minute;
        } else if (minute > 59) {
            hour = hour + 1;
            minute = minute - 60;
        }
        return transformClockInTime(hour, minute, second);
    }

    //打卡时间转换
    private Date transformClockInTime(int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, second);
        Date date = calendar.getTime();
        // 如果第一次执行定时任务的时间 小于 当前的时间 要在 第一次执行定时任务的时间 加一天，以便此任务在下个时间点执行。如果不加一天，任务会立即执行。
        if (isFirstRun) {
            if (date.before(new Date())) {
                date = addDay(date, 1);
            }
        } else {
            date = addDay(date, 1);
        }
        return date; //第一次执行定时任务的时间
    }

    // 增加或减少天数
    private Date addDay(Date date, int num) {
        Calendar startDT = Calendar.getInstance();
        startDT.setTime(date);
        startDT.add(Calendar.DAY_OF_MONTH, num);
        return startDT.getTime();
    }

    //获取随机数
    public int getRandomMill(int maxNum, boolean needMinus) {
        if (maxNum == 0) return 0;
        Random random = new Random();
        int num = random.nextInt(maxNum + 1);
        if (needMinus) {
            num = num * (new Random().nextInt(2) == 0 ? -1 : 1);
        }
        return num;
    }

    //打卡操作
    private void clockInAction(User user) {
        int count = 0;
        while (count < RETRY_NUM) {
            try {
                print(user.getAlias() + "-Start");
                // 模拟一个浏览器
                WebClient webClient = new WebClient(BrowserVersion.getDefault());
                webClient.getOptions().setThrowExceptionOnScriptError(false);
                webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
                webClient.getOptions().setJavaScriptEnabled(true);
                webClient.getOptions().setActiveXNative(false);
                webClient.getOptions().setCssEnabled(false);
                webClient.getOptions().setThrowExceptionOnScriptError(false);
                webClient.waitForBackgroundJavaScript(10 * 1000);
                webClient.setAjaxController(new NicelyResynchronizingAjaxController());
                webClient.getOptions().setJavaScriptEnabled(true);
                HtmlPage rootPage = webClient.getPage("http://kq.neusoft.com/");    //网址
                Thread.sleep(3000);//主要是这个线程的等待 因为js加载也是需要时间的
                //获取用户输入框
                HtmlForm form = rootPage.getFormByName("LoginForm");
                HtmlInput htmlInput = form.getInputByName("neusoft_key");
                int index = htmlInput.getDefaultValue().indexOf("PWD");
                String userNamekey = htmlInput.getDefaultValue().substring(index + 3, htmlInput.getDefaultValue().length());
                String userNameInputID = "nt" + userNamekey;
                HtmlInput userNameInput = (HtmlInput) rootPage.getElementById(userNameInputID);
                String userNameInputName = userNameInput.getOriginalName();
                String userPwdInputID = "KEY" + userNameInputName.substring(2);
                HtmlInput userPwdInput = form.getInputByName(userPwdInputID);
                String checkCodeInputID = "YZM" + userNameInputName.substring(2);
                HtmlInput checkCodeInput = form.getInputByName(checkCodeInputID);
                HtmlImage checkCodeImg = form.getOneHtmlElementByAttribute("img", "id", "imgRandom");
                BufferedImage bufferedImage = checkCodeImg.getImageReader().read(0);

                //创建临时目录
                File file = new File(CHECK_CODE_PATH); //此目录保存缩小后的关键图
                if (!file.exists()) {
                    //如果要创建的多级目录不存在才需要创建。
                    file.mkdirs();
                }
                File checkCodeFile = new File(CHECK_CODE_PATH + CHECK_CODE_NAME);
                if (checkCodeFile.exists()) {
                    checkCodeFile.delete();
                }
                File ocrCheckCodeFile = new File(CHECK_CODE_PATH + OCR_CHECK_CODE_NAME);
                if (ocrCheckCodeFile.exists()) {
                    ocrCheckCodeFile.delete();
                }
                //保存图片
                ImageIO.write(bufferedImage, "jpg", new File(CHECK_CODE_PATH + CHECK_CODE_NAME)); //将其保存在C:/imageSort/targetPIC/下
                //去噪点
                ImgUtils.removeBackground(CHECK_CODE_PATH + CHECK_CODE_NAME, CHECK_CODE_PATH + OCR_CHECK_CODE_NAME);
                //裁剪边角
                ImgUtils.cuttingImg(CHECK_CODE_PATH + OCR_CHECK_CODE_NAME);
                String codeString = getCheckCode(CHECK_CODE_PATH + OCR_CHECK_CODE_NAME);
                print("CheckCode:" + codeString);
                HtmlInput login = form.getInputByValue("登入系统");
                userNameInput.focus();
                userNameInput.setDefaultValue(user.getUserName());
                userPwdInput.focus();
                userPwdInput.setDefaultValue(user.getUserPwd());
                checkCodeInput.focus();
                checkCodeInput.setDefaultValue(codeString);
                login.focus();
                //登陆操作
                HtmlPage attendancePage = login.click();
                HtmlAnchor htmlAnchor = attendancePage.querySelector(".mr36");
                print(user.getUserName() + "登陆成功");
                //打卡操作
                Thread.sleep(2000);
                htmlAnchor.click();
                Thread.sleep(2000);
                //退出操作
              /*  DomNode domNode = attendancePage.querySelector(".kq-btn");
                DomNodeList<DomNode> a = domNode.querySelectorAll("a");
                HtmlAnchor exit = (HtmlAnchor) a.get(1);
                exit.click();*/
                //关闭webclient
                webClient.close();
                print(user.getAlias() + "-END");
                break;
            } catch (Throwable e) {
                print(user.getAlias() + "-FAIL");
                e.printStackTrace();
                count = count + 1;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }

    }

    //初始化Test4j并获取Check code
    private String getCheckCode(String imgUrl) throws Exception {
        ITesseract instance = new Tesseract();
        File imgDir = new File(imgUrl);
        String codeString = instance.doOCR(imgDir);
        if (codeString != null && !"".equals(codeString)) {
            codeString = codeString.trim();
            if (codeString.contains(":")) { //此处特殊处理
                codeString = codeString.replace(":", "3");
            }
        } else {
            throw new Exception("check code check fail!");
        }
        return codeString;
    }

    //日志输出
    private static void print(String str) {
        System.err.println(">  " + str);
    }

    //上班打卡定时任务
    class ClockInWorkOnTask extends TimerTask {
        @Override
        public void run() {
            try {
                if (NEED_C0LOCKIN_FOR_WEEKENDS) {
                    startClockInAction();
                } else {
                    SimpleDateFormat currentWeekFormat = new SimpleDateFormat("EEEE");//当天星期
                    Date date = new Date(System.currentTimeMillis());
                    String currentWeek = currentWeekFormat.format(date);
                    if (!ArrayUtils.contains(DATE_FILETER, currentWeek)) {
                        startClockInAction();
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                executeWorkOnColockInTask();
//                System.exit(0);
            }
        }
    }

    //下班打卡定时任务
    class ClockInWorkOffTask extends TimerTask {
        @Override
        public void run() {
            try {
                if (NEED_C0LOCKIN_FOR_WEEKENDS) {
                    startClockInAction();
                } else {
                    SimpleDateFormat currentWeekFormat = new SimpleDateFormat("EEEE");//当天星期
                    Date date = new Date(System.currentTimeMillis());
                    String currentWeek = currentWeekFormat.format(date);
                    if (!ArrayUtils.contains(DATE_FILETER, currentWeek)) {
                        startClockInAction();
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                executeWorkOffColockInTask();
//                System.exit(0);
            }
        }
    }

    //进行打卡
    private void startClockInAction() throws InterruptedException {
        List<User> userList = getUserList();
        if (userList != null) {
            for (User user : userList) {
                clockInAction(user);
            }
        }
    }

    //读取本地JSON文件获取用户账户信息
    private List<User> getUserList() {
        try {
            List<User> userList = new ArrayList<>();
            String jsonStr = readJson("userConfig.json");
            if (jsonStr == null) return null;
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String userName = jsonObject.getString("userName");
                String userPwd = jsonObject.getString("userPwd");
                String alias = jsonObject.getString("alias");
                User user = new User(userName, userPwd, alias);
                userList.add(user);
            }
            return userList;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //读取JSON用户数据
    public String readJson(String fileName) {
        BufferedReader reader = null;
        //返回值,使用StringBuffer
        StringBuffer data = new StringBuffer();
        try {
            //不可写绝对路径 否则导出的jar找不到配置文件
            ClassLoader classloader = Thread.currentThread().getContextClassLoader();
            InputStream inputStream = classloader.getResourceAsStream("com/neusoft/colockin/" + fileName);
            reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            //每次读取文件的缓存
            String temp = null;
            while ((temp = reader.readLine()) != null) {
                data.append(temp);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //关闭文件流
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return data.toString();
    }

    //用户
    class User {

        public User(String userName, String userPwd, String alias) {
            this.userName = userName;
            this.userPwd = userPwd;
            this.alias = alias;
        }

        private String userName;
        private String userPwd;
        private String alias;

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getUserPwd() {
            return userPwd;
        }

        public void setUserPwd(String userPwd) {
            this.userPwd = userPwd;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }
    }

    public static class ImgUtils {

        public static void removeBackground(String imgUrl, String resUrl) {
            //定义一个临界阈值
            int threshold = 300;
            try {
                BufferedImage img = ImageIO.read(new File(imgUrl));
                int width = img.getWidth();
                int height = img.getHeight();
                for (int i = 1; i < width; i++) {
                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < height; y++) {
                            Color color = new Color(img.getRGB(x, y));
                            int num = color.getRed() + color.getGreen() + color.getBlue();
                            if (num >= threshold) {
                                img.setRGB(x, y, Color.WHITE.getRGB());
                            }
                        }
                    }
                }
                for (int i = 1; i < width; i++) {
                    Color color1 = new Color(img.getRGB(i, 1));
                    int num1 = color1.getRed() + color1.getGreen() + color1.getBlue();
                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < height; y++) {
                            Color color = new Color(img.getRGB(x, y));

                            int num = color.getRed() + color.getGreen() + color.getBlue();
                            if (num == num1) {
                                img.setRGB(x, y, Color.BLACK.getRGB());
                            } else {
                                img.setRGB(x, y, Color.WHITE.getRGB());
                            }
                        }
                    }
                }
                File file = new File(resUrl);
                if (!file.exists()) {
                    File dir = file.getParentFile();
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    try {
                        file.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                ImageIO.write(img, "jpg", file);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void cuttingImg(String imgUrl) {
            try {
                File newfile = new File(imgUrl);
                BufferedImage bufferedimage = ImageIO.read(newfile);
                int width = bufferedimage.getWidth();
                int height = bufferedimage.getHeight();
                if (width > 52) {
                    bufferedimage = ImgUtils.cropImage(bufferedimage, (int) ((width - 52) / 2), 0, (int) (width - (width - 52) / 2), (int) (height));
                    if (height > 16) {
                        bufferedimage = ImgUtils.cropImage(bufferedimage, 0, (int) ((height - 16) / 2), 52, (int) (height - (height - 16) / 2));
                    }
                } else {
                    if (height > 16) {
                        bufferedimage = ImgUtils.cropImage(bufferedimage, 0, (int) ((height - 16) / 2), (int) (width), (int) (height - (height - 16) / 2));
                    }
                }
                ImageIO.write(bufferedimage, "jpg", new File(imgUrl));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public static BufferedImage cropImage(BufferedImage bufferedImage, int startX, int startY, int endX, int endY) {
            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            if (startX == -1) {
                startX = 0;
            }
            if (startY == -1) {
                startY = 0;
            }
            if (endX == -1) {
                endX = width - 1;
            }
            if (endY == -1) {
                endY = height - 1;
            }
            BufferedImage result = new BufferedImage(endX - startX, endY - startY, 4);
            for (int x = startX; x < endX; ++x) {
                for (int y = startY; y < endY; ++y) {
                    int rgb = bufferedImage.getRGB(x, y);
                    result.setRGB(x - startX, y - startY, rgb);
                }
            }
            return result;
        }
    }

    public static void main(String[] args) {
        new ColockInManager().executeColockInTask();

    }

}


