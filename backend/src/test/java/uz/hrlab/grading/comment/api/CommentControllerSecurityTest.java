package uz.hrlab.grading.comment.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.comment.application.CreateCommentUseCase;
import uz.hrlab.grading.comment.application.DeleteCommentUseCase;
import uz.hrlab.grading.comment.application.ListCommentsByEntityQuery;
import uz.hrlab.grading.comment.application.ListMyMentionsQuery;
import uz.hrlab.grading.comment.application.UpdateCommentUseCase;
import uz.hrlab.grading.comment.domain.Comment;
import uz.hrlab.grading.comment.domain.CommentEntityType;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
@WebMvcTest(controllers = CommentController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class CommentControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean CreateCommentUseCase createUseCase;
    @MockBean UpdateCommentUseCase updateUseCase;
    @MockBean DeleteCommentUseCase deleteUseCase;
    @MockBean ListCommentsByEntityQuery listByEntity;
    @MockBean ListMyMentionsQuery mentionsQuery;
    @MockBean AuditService audit;

    @Test
    void anonymousCreateIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entity_type\":\"POSITION\",\"entity_id\":\""
                                 + UUID.randomUUID() + "\",\"body\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithoutPermissionIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/comments")
                        .with(jwt().authorities(() -> "PROJECT_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entity_type\":\"POSITION\",\"entity_id\":\""
                                 + UUID.randomUUID() + "\",\"body\":\"hi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithCommentCreateSucceeds() throws Exception {
        given(createUseCase.create(any())).willReturn(stubComment(UUID.randomUUID()));
        mvc.perform(post("/api/v1/comments")
                        .with(jwt().authorities(() -> "COMMENT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entity_type\":\"POSITION\",\"entity_id\":\""
                                 + UUID.randomUUID() + "\",\"body\":\"hi\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void editRequiresEditPermissionNotCreate() throws Exception {
        mvc.perform(patch("/api/v1/comments/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "COMMENT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRequiresDeletePermission() throws Exception {
        mvc.perform(delete("/api/v1/comments/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "COMMENT_EDIT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteWithPermissionReturns204() throws Exception {
        mvc.perform(delete("/api/v1/comments/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "COMMENT_DELETE")))
                .andExpect(status().isNoContent());
    }

    private Comment stubComment(UUID id) {
        return new Comment(id, UUID.randomUUID(), UUID.randomUUID(),
                CommentEntityType.POSITION, UUID.randomUUID(),
                UUID.randomUUID(), "hello", Set.of(), null,
                OffsetDateTime.now(), null, null);
    }
}
