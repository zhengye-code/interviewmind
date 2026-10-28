package com.interviewmind.infrastructure.file;

import com.interviewmind.common.exception.BusinessException;
import com.interviewmind.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Component
public class DocumentParseService {

    private final Parser parser = new AutoDetectParser();
    private final Tika tika = new Tika();

    /**
     * 探测文件的真实 MIME 类型。
     *
     * 注意：【故意不传文件名】。Tika 的 detect(stream, name) 会参考文件名，
     * 等于相信客户端的命名，改个后缀就能绕过校验。
     * 纯内容探测对 DOCX 也没问题：Tika 会解压 ZIP 读 [Content_Types].xml 来判断。
     *
     * 用 TikaInputStream 包一层，是为了让流支持 mark/reset（容器类格式需要回退读取）。
     */
    public String detectContentType(MultipartFile file) {
        try (InputStream raw = file.getInputStream();
             TikaInputStream in = TikaInputStream.get(raw)) {
            return tika.detect(in);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "无法识别文件类型", e);
        }
    }

    /** 把文档解析成纯文本 */
    public String parseToText(MultipartFile file) {
        try (InputStream raw = file.getInputStream();
             TikaInputStream in = TikaInputStream.get(raw)) {
            Metadata metadata = new Metadata();
            // 解析阶段可以带文件名，帮助 AutoDetectParser 选解析器；类型校验已在前面完成
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());

            // -1 表示不限制提取长度（生产环境建议设上限，防止超大文档吃满内存）
            BodyContentHandler handler = new BodyContentHandler(-1);

            parser.parse(in, handler, metadata, new ParseContext());
            return handler.toString();
        } catch (Exception e) {
            log.warn("文档解析失败: filename={}, error={}", file.getOriginalFilename(), e.getMessage());
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "简历解析失败，请确认文件未损坏", e);
        }
    }
}
