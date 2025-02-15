package cn.keking.web.controller;

import cn.keking.constant.FileViewConstant;
import cn.keking.model.FileAttribute;
import cn.keking.service.FileDownloadService;
import cn.keking.service.FilePreview;
import cn.keking.service.FilePreviewFactory;

import cn.keking.service.cache.CacheService;
import cn.keking.utils.FileUtils;
import cn.keking.utils.PathUtil;
import com.github.tobato.fastdfs.service.FastFileStorageClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author yudian-it
 */
@Slf4j
@Controller
public class OnlinePreviewController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnlinePreviewController.class);

    @Autowired
    FilePreviewFactory previewFactory;

    @Autowired
    CacheService cacheService;


    @Value("${file.dir}")
    private String filePreviewPath;

    @Value("${fdfs.path}")
    private String fdfsPath;

    @Value("${file.download.type}")
    private String fileDownloadType;

    @Autowired
    private FileUtils fileUtils;

    @Autowired
    private FileDownloadService fileDownloadService;

    /**
     * @param url   http://ip:port/demo/demo.docx
     * @param path  group1/M00/00/01/wKj4RFvuZQeAFe4nAAJZc0IRPSY60.docx
     * @param model 视图
     * @param req   request
     * @return
     * @throws UnsupportedEncodingException
     */
    @RequestMapping(value = "onlinePreview", method = RequestMethod.GET)
    public String onlinePreview(String url, String path, Model model, HttpServletRequest req, HttpServletResponse response) throws UnsupportedEncodingException, IOException {


        FileAttribute fileAttribute = fileUtils.getFileAttribute(url);
        String savePath = filePreviewPath + "demo" + File.separator;

        if (FileViewConstant.FILE_DOWNLOAD_TYPE_HTTP.equals(fileDownloadType)) {
            // 使用网络URL下载文件
            log.info("【网络URL文件下载】正在从url：{}下载文件", url);

        } else if (path != null && FileViewConstant.FILE_DOWNLOAD_TYPE_LOCAL.equals(fileDownloadType)) {
            //本地文件复制
            String sourcePath = fdfsPath + path.substring(PathUtil.getCharacterPosition(path, 2, "/") + 1, path.length());
            log.info("【本地文件下载】正在从{}复制文件到{}", sourcePath, savePath);
            fileDownloadService.downLoadFromLocal(sourcePath, savePath);
        } else if (path != null && FileViewConstant.FILE_DOWNLOAD_TYPE_FASTDFSCLIENT.equals(fileDownloadType)) {
            //使用FastDFS Client 下载文件
            String group = path.substring(0, PathUtil.getCharacterPosition(path, 1, "/"));
            fileDownloadService.downLoadFromFastDFS(group,
                    path.substring(PathUtil.getCharacterPosition(path, 1, "/") + 1,
                            path.length()), savePath, fileAttribute, response);

        }

        req.setAttribute("fileKey", req.getParameter("fileKey"));
        model.addAttribute("officePreviewType", req.getParameter("officePreviewType"));
        model.addAttribute("originUrl", req.getRequestURL().toString());
        FilePreview filePreview = previewFactory.get(fileAttribute);
        return filePreview.filePreviewHandle(url, model, fileAttribute);
    }

    /**
     * 多图片切换预览
     *
     * @param model
     * @param req
     * @return
     * @throws UnsupportedEncodingException
     */
    @RequestMapping(value = "picturesPreview", method = RequestMethod.GET)
    public String picturesPreview(String urls, String currentUrl, Model model, HttpServletRequest req) throws UnsupportedEncodingException {
        // 路径转码
        String decodedUrl = URLDecoder.decode(urls, "utf-8");
        String decodedCurrentUrl = URLDecoder.decode(currentUrl, "utf-8");
        // 抽取文件并返回文件列表
        String[] imgs = decodedUrl.split("\\|");
        List imgurls = Arrays.asList(imgs);
        model.addAttribute("imgurls", imgurls);
        model.addAttribute("currentUrl", decodedCurrentUrl);
        return "picture";
    }

    @RequestMapping(value = "picturesPreview", method = RequestMethod.POST)
    public String picturesPreview(Model model, HttpServletRequest req) throws UnsupportedEncodingException {
        String urls = req.getParameter("urls");
        String currentUrl = req.getParameter("currentUrl");
        // 路径转码
        String decodedUrl = URLDecoder.decode(urls, "utf-8");
        String decodedCurrentUrl = URLDecoder.decode(currentUrl, "utf-8");
        // 抽取文件并返回文件列表
        String[] imgs = decodedUrl.split("\\|");
        List imgurls = Arrays.asList(imgs);
        model.addAttribute("imgurls", imgurls);
        model.addAttribute("currentUrl", decodedCurrentUrl);
        return "picture";
    }

    /**
     * 根据url获取文件内容
     * 当pdfjs读取存在跨域问题的文件时将通过此接口读取
     *
     * @param urlPath
     * @param resp
     */
    @RequestMapping(value = "/getCorsFile", method = RequestMethod.GET)
    public void getCorsFile(String urlPath, HttpServletResponse resp) {
        InputStream inputStream = null;
        try {
            String strUrl = urlPath.trim();
            URL url = new URL(new URI(strUrl).toASCIIString());
            //打开请求连接
            URLConnection connection = url.openConnection();
            HttpURLConnection httpURLConnection = (HttpURLConnection) connection;
            httpURLConnection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
            inputStream = httpURLConnection.getInputStream();
            byte[] bs = new byte[1024];
            int len;
            while (-1 != (len = inputStream.read(bs))) {
                resp.getOutputStream().write(bs, 0, len);
            }
        } catch (IOException | URISyntaxException e) {
            LOGGER.error("下载pdf文件失败", e);
        } finally {
            if (inputStream != null) {
                IOUtils.closeQuietly(inputStream);
            }
        }
    }

    /**
     * 通过api接口入队
     *
     * @param url 请编码后在入队
     */
    @GetMapping("/addTask")
    @ResponseBody
    public String addQueueTask(String url) {
        cacheService.addQueueTask(url);
        return "success";
    }

}
