package com.mingo.backend.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.common.security.CustomUserDetailsService;
import com.mingo.backend.common.security.JwtService;
import com.mingo.backend.post.dto.AuthorSummary;
import com.mingo.backend.post.dto.CommentRequest;
import com.mingo.backend.post.dto.CreatePostRequest;
import com.mingo.backend.post.dto.PostResponse;
import com.mingo.backend.post.dto.ReactionRequest;
import com.mingo.backend.post.dto.ReportPostRequest;
import com.mingo.backend.post.dto.UpdatePostRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostService postService;

    // JwtAuthenticationFilter is picked up as a Filter bean by @WebMvcTest and needs these
    // constructor dependencies satisfied even though addFilters=false skips running it.
    @MockBean
    private JwtService jwtService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    // With addFilters=false no security filter runs, so the mock request's principal is never
    // populated from a SecurityContext — @WithMockUser alone leaves Authentication null in the
    // controller. Attaching the principal directly to each request is what the plain
    // `Authentication auth` controller parameter actually reads.
    private static final Authentication ME =
            new UsernamePasswordAuthenticationToken("me@example.com", null);

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.principal(ME);
    }

    private PostResponse samplePost() {
        return new PostResponse(UUID.randomUUID(), new AuthorSummary(UUID.randomUUID(), "Tac Gia", null),
                "hello world", List.of(), "PUBLIC", Map.of("like", 0L), null, List.of(), false, false, Instant.now());
    }

    @Test
    void createPost_returns201_whenValid() throws Exception {
        when(postService.createPost(eq("me@example.com"), any(CreatePostRequest.class))).thenReturn(samplePost());

        mockMvc.perform(authed(post("/posts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePostRequest("hello world", List.of(), PostVisibility.PUBLIC))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("hello world"));
    }

    @Test
    void createPost_returns400_whenContentTooLong() throws Exception {
        String tooLong = "a".repeat(5001);

        mockMvc.perform(authed(post("/posts"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePostRequest(tooLong, List.of(), PostVisibility.PUBLIC))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    void getFeed_returns200WithPage() throws Exception {
        Page<PostResponse> page = new PageImpl<>(List.of(samplePost()), PageRequest.of(0, 20), 1);
        when(postService.getFeed(eq("me@example.com"), any())).thenReturn(page);

        mockMvc.perform(authed(get("/posts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("hello world"));
    }

    @Test
    void getPost_returns404_whenServiceReportsNotFound() throws Exception {
        when(postService.getPost(eq("me@example.com"), any(UUID.class)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết"));

        mockMvc.perform(authed(get("/posts/{id}", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy bài viết"));
    }

    @Test
    void getPost_returns403_whenServiceReportsForbidden() throws Exception {
        when(postService.getPost(eq("me@example.com"), any(UUID.class)))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem bài viết này"));

        mockMvc.perform(authed(get("/posts/{id}", UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePost_returns200_whenValid() throws Exception {
        when(postService.updatePost(eq("me@example.com"), any(UUID.class), any(UpdatePostRequest.class)))
                .thenReturn(samplePost());

        mockMvc.perform(authed(patch("/posts/{id}", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePostRequest("updated", List.of(), PostVisibility.FRIENDS))))
                .andExpect(status().isOk());
    }

    @Test
    void updatePost_returns403_whenNotTheAuthor() throws Exception {
        when(postService.updatePost(eq("me@example.com"), any(UUID.class), any(UpdatePostRequest.class)))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền sửa bài viết này"));

        mockMvc.perform(authed(patch("/posts/{id}", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePostRequest("updated", List.of(), null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletePost_returns204_onSuccess() throws Exception {
        mockMvc.perform(authed(delete("/posts/{id}", UUID.randomUUID())))
                .andExpect(status().isNoContent());

        verify(postService).deletePost(eq("me@example.com"), any(UUID.class));
    }

    @Test
    void addComment_returns201_whenValid() throws Exception {
        var commentResponse = new com.mingo.backend.post.dto.CommentResponse(UUID.randomUUID(),
                new AuthorSummary(UUID.randomUUID(), "Tac Gia", null), "nice post!", null, Instant.now(), 0L, false, false, List.of());
        when(postService.addComment(eq("me@example.com"), any(UUID.class), any(CommentRequest.class)))
                .thenReturn(commentResponse);

        mockMvc.perform(authed(post("/posts/{id}/comments", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("nice post!", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("nice post!"));
    }

    @Test
    void addComment_returns400_whenContentBlank() throws Exception {
        mockMvc.perform(authed(post("/posts/{id}/comments", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    void setReaction_returns400_whenTypeMissing() throws Exception {
        mockMvc.perform(authed(put("/posts/{id}/reaction", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.type").exists());
    }

    @Test
    void setReaction_returns200_whenValid() throws Exception {
        when(postService.setReaction(eq("me@example.com"), any(UUID.class), any(ReactionRequest.class)))
                .thenReturn(samplePost());

        mockMvc.perform(authed(put("/posts/{id}/reaction", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReactionRequest(ReactionType.LIKE))))
                .andExpect(status().isOk());
    }

    @Test
    void reportPost_returns400_whenReasonBlank() throws Exception {
        mockMvc.perform(authed(post("/posts/{id}/report", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportPostRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
    }

    @Test
    void savePost_returns200_onSuccess() throws Exception {
        when(postService.savePost(eq("me@example.com"), any(UUID.class))).thenReturn(samplePost());

        mockMvc.perform(authed(put("/posts/{id}/save", UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedByMe").value(false));
    }

    @Test
    void searchPosts_returns200WithPage() throws Exception {
        Page<PostResponse> page = new PageImpl<>(List.of(samplePost()), PageRequest.of(0, 10), 1);
        when(postService.searchPosts(eq("me@example.com"), anyString(), any())).thenReturn(page);

        mockMvc.perform(authed(get("/posts/search").param("q", "hello")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("hello world"));
    }
}
