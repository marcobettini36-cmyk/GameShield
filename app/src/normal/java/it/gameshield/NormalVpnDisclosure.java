package it.gameshield;

/** The same separate, affirmative VPN disclosure for both Normal entry points. */
final class NormalVpnDisclosure {
    private NormalVpnDisclosure() {}
    static final String TEXT = "GameShield usa una VPN locale per confrontare domini DNS, hostname HTTP/TLS visibili, IP e porte con le regole di blocco. Questi dati sono elaborati sul telefono per bloccare il gioco d'azzardo; non sono inviati a un server GameShield né usati per pubblicità. Non decifra HTTPS e non conserva una cronologia di navigazione. "
        + "Il traffico consentito prosegue verso i siti e i resolver DNS configurati, che ricevono le normali richieste. Non offre una VPN remota né anonimato. Gli aggiornamenti della blacklist contattano GitHub. Android chiederà il consenso: GameShield sostituisce eventuali altre VPN e resta revocabile. Consenti questo filtro locale?";
}
