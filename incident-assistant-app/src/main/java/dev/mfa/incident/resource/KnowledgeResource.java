package dev.mfa.incident.resource;

import dev.mfa.incident.dto.IngestionReportDto;
import dev.mfa.incident.service.KnowledgeIngestionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Administration of the knowledge base (A-0004, A-0013). */
@Path("/v1/knowledge")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Knowledge")
@APIResponse(
    responseCode = "401",
    description = "Not authenticated",
    content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
@APIResponse(
    responseCode = "403",
    description = "Role missing",
    content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
public class KnowledgeResource {

  private final KnowledgeIngestionService ingestion;

  public KnowledgeResource(KnowledgeIngestionService ingestion) {
    this.ingestion = ingestion;
  }

  @POST
  @Path("/ingestion")
  @RolesAllowed(Roles.KNOWLEDGE_ADMIN)
  @Operation(
      summary = "Ingest the knowledge directory now",
      description =
          "Adds new documents, re-embeds changed ones, removes deleted ones and reports files"
              + " that are not valid knowledge documents. Runs synchronously.")
  @APIResponse(responseCode = "200", description = "What the run did")
  @APIResponse(
      responseCode = "409",
      description = "Another ingestion is running",
      content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
  public IngestionReportDto ingest() {
    return ingestion.ingest();
  }
}
