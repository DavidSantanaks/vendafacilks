package br.com.vendafacil.identity.recovery;

public record RecoveryMail(String recipient, String portalName, String link, boolean completed) {}
