package br.com.vendafacil.identity.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RecoveryMailer {
  private static final Logger LOG = LoggerFactory.getLogger(RecoveryMailer.class);
  private final JavaMailSender sender;
  private final String from;

  public RecoveryMailer(JavaMailSender sender, @Value("${app.mail-from}") String from) {
    this.sender = sender;
    this.from = from;
  }

  @Async("recoveryExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void send(RecoveryMail event) {
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(event.recipient());
    message.setSubject(
        event.completed()
            ? "Sua senha foi alterada | vendaFacil"
            : "Recuperação de senha | vendaFacil");
    message.setText(
        event.completed()
            ? "A senha da sua conta foi alterada. Suas sessões anteriores foram encerradas.\n\nEntre novamente: "
                + event.link()
                + "\n\nSe não foi você, procure o administrador do portal."
            : "Recebemos um pedido de recuperação no portal "
                + event.portalName()
                + ".\n\nDefina sua nova senha: "
                + event.link()
                + "\n\nO link pode ser usado uma única vez e expira em 30 minutos. Se não pediu esta alteração, ignore este e-mail. Sua senha continua válida.");
    try {
      sender.send(message);
    } catch (MailException exception) {
      LOG.error(
          "Falha no envio de recuperação de senha: category={}",
          exception.getClass().getSimpleName());
    }
  }
}
