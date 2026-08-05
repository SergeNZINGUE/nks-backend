package bf.laterrasse.nks.aop;

import bf.laterrasse.nks.domain.AuditLog;
import bf.laterrasse.nks.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Écrit automatiquement une ligne append-only dans audit_logs pour chaque méthode
 * annotée @Auditable qui se termine sans exception (§14.11). N'échoue jamais l'appel
 * métier : toute erreur d'audit est journalisée en WARN et avalée.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void auditer(org.aspectj.lang.JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            UUID entiteId = extraireId(result);
            UUID utilisateurId = extraireUtilisateurCourant();

            AuditLog log = AuditLog.builder()
                    .action(auditable.action())
                    .entiteConcernee(auditable.entite())
                    .entiteId(entiteId)
                    .ipSource(extraireIp())
                    .userAgent(extraireUserAgent())
                    .build();

            if (utilisateurId != null) {
                log.setUtilisateur(bf.laterrasse.nks.domain.Utilisateur.builder().id(utilisateurId).build());
            }

            auditLogRepository.save(log);
        } catch (Exception e) {
            AuditAspect.log.warn("Échec écriture AuditLog pour {} : {}", auditable.action(), e.getMessage());
        }
    }

    private UUID extraireId(Object result) {
        if (result == null) {
            return null;
        }
        try {
            Method getId = result.getClass().getMethod("getId");
            Object id = getId.invoke(result);
            return id instanceof UUID uuid ? uuid : null;
        } catch (Exception e) {
            return null;
        }
    }

    private UUID extraireUtilisateurCourant() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return principal instanceof UUID uuid ? uuid : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extraireIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest().getRemoteAddr() : null;
    }

    private String extraireUserAgent() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest().getHeader("User-Agent") : null;
    }
}
