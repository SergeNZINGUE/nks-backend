package bf.laterrasse.nks.gateway.social;

public record EngagementSocial(
        String plateforme,     // FACEBOOK | TIKTOK
        String postId,
        String snapshotId,     // identifiant unique du relevé (pour déduplication via Vote.sourceExterneId)
        long nombreLikes,
        long nombreCommentaires
) {
}
