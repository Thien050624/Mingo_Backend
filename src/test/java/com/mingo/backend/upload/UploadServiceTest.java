package com.mingo.backend.upload;

import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.upload.dto.UploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadServiceTest {

    private UploadService uploadService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        uploadService = new UploadService(tempDir.toString(), "http://localhost:8080");
    }

    @Test
    void store_rejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "a.exe", "application/x-msdownload", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> uploadService.store(file))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void store_rejectsFileWhoseBytesDontMatchDeclaredType() {
        // Declares itself a PNG but carries plain text bytes — no PNG signature present.
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png",
                "not a real png".getBytes());

        assertThatThrownBy(() -> uploadService.store(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không khớp với định dạng khai báo");
    }

    @Test
    void store_acceptsFileWithGenuinePngSignature() {
        byte[] pngSignature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "real.png", "image/png", pngSignature);

        UploadResponse response = uploadService.store(file);

        assertThat(response.url()).contains(".png");
    }

    @Test
    void store_acceptsFileWithGenuineJpegSignature() {
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "real.jpg", "image/jpeg", jpegSignature);

        UploadResponse response = uploadService.store(file);

        assertThat(response.url()).contains(".jpg");
    }

    @Test
    void store_rejectsTextFileContainingBinaryData() {
        byte[] binaryLooking = {'h', 'e', 'l', 'l', 'o', 0, 'w', 'o', 'r', 'l', 'd'};
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", binaryLooking);

        assertThatThrownBy(() -> uploadService.store(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không khớp với định dạng khai báo");
    }

    @Test
    void store_acceptsGenuinePlainTextFile() {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello world".getBytes());

        UploadResponse response = uploadService.store(file);

        assertThat(response.url()).contains(".txt");
    }

    @Test
    void store_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> uploadService.store(file))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void store_acceptsFileWithGenuineWebpSignature() {
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        MockMultipartFile file = new MockMultipartFile("file", "real.webp", "image/webp", webp);

        assertThat(uploadService.store(file).url()).contains(".webp");
    }

    @Test
    void store_rejectsWebpDeclaredFile_missingWebpMarkerAfterRiff() {
        // Has the RIFF container prefix but isn't actually a WEBP payload (e.g. a WAV file).
        byte[] riffWav = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
        MockMultipartFile file = new MockMultipartFile("file", "fake.webp", "image/webp", riffWav);

        assertThatThrownBy(() -> uploadService.store(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không khớp với định dạng khai báo");
    }

    @Test
    void store_acceptsFileWithGenuineGifSignature() {
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "real.gif", "image/gif", gif);

        assertThat(uploadService.store(file).url()).contains(".gif");
    }

    @Test
    void store_acceptsFileWithGenuineMp4Signature() {
        byte[] mp4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        MockMultipartFile file = new MockMultipartFile("file", "real.mp4", "video/mp4", mp4);

        assertThat(uploadService.store(file).url()).contains(".mp4");
    }

    @Test
    void store_rejectsMp4DeclaredFile_withoutIsoBaseMediaBox() {
        byte[] garbage = {0, 0, 0, 0, 'x', 'x', 'x', 'x', 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "fake.mp4", "video/mp4", garbage);

        assertThatThrownBy(() -> uploadService.store(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không khớp với định dạng khai báo");
    }

    @Test
    void store_acceptsFileWithGenuineWebmSignature() {
        byte[] webm = {(byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "real.webm", "video/webm", webm);

        assertThat(uploadService.store(file).url()).contains(".webm");
    }

    @Test
    void store_acceptsFileWithGenuineOggSignature() {
        byte[] ogg = {'O', 'g', 'g', 'S', 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "real.ogv", "video/ogg", ogg);

        assertThat(uploadService.store(file).url()).contains(".ogv");
    }

    @Test
    void store_acceptsFileWithGenuinePdfSignature() {
        byte[] pdf = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
        MockMultipartFile file = new MockMultipartFile("file", "real.pdf", "application/pdf", pdf);

        assertThat(uploadService.store(file).url()).contains(".pdf");
    }

    @Test
    void store_rejectsPdfDeclaredFile_thatIsActuallyPlainText() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", "just text".getBytes());

        assertThatThrownBy(() -> uploadService.store(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không khớp với định dạng khai báo");
    }

    @Test
    void store_acceptsFileWithGenuineOleCompoundSignature_forLegacyDoc() {
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        MockMultipartFile file = new MockMultipartFile("file", "real.doc", "application/msword", ole);

        assertThat(uploadService.store(file).url()).contains(".doc");
    }

    @Test
    void store_acceptsFileWithGenuineZipSignature_forModernDocx() {
        byte[] zip = {'P', 'K', 0x03, 0x04, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "real.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", zip);

        assertThat(uploadService.store(file).url()).contains(".docx");
    }

    @Test
    void store_rejectsDocxDeclaredFile_withoutZipSignature() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "not a zip".getBytes());

        assertThatThrownBy(() -> uploadService.store(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không khớp với định dạng khai báo");
    }

    @Test
    void store_acceptsFileWithGenuineZipSignature() {
        byte[] zip = {'P', 'K', 0x03, 0x04, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "real.zip", "application/zip", zip);

        assertThat(uploadService.store(file).url()).contains(".zip");
    }

    @Test
    void store_acceptsFileWithGenuineRarSignature() {
        byte[] rar = {'R', 'a', 'r', '!', 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "real.rar", "application/x-rar-compressed", rar);

        assertThat(uploadService.store(file).url()).contains(".rar");
    }

    @Test
    void store_rejectsSpoofedExtension_zipContentDeclaredAsJpeg() {
        // A classic attack: rename a .zip to .jpg and lie about the Content-Type.
        byte[] zip = {'P', 'K', 0x03, 0x04, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "malicious.jpg", "image/jpeg", zip);

        assertThatThrownBy(() -> uploadService.store(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không khớp với định dạng khai báo");
    }
}
