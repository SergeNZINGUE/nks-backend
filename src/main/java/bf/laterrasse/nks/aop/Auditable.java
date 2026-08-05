package bf.laterrasse.nks.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marque une méthode de service dont l'exécution réussie doit être journalisée dans
 * audit_logs (§14.11 : validation/rejet candidature, note jury, ticket scanné, résultats
 * publiés, correction admin...). L'entité retournée par la méthode doit exposer getId()
 * pour que AuditAspect puisse renseigner entite_id.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {
    String action();
    String entite();
}
