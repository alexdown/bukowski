----------------------------------------------------------------------------

                                                   Java Bukowski mail server
                                       (with SPF sender verification system)

----------------------------------------------------------------------------

(c) 2004 by the Bukowski Team
Basso Alessio, Di Giovinazzo Matteo, Penzo Peter
(in ordine rigorosamente alfabetico)


COME INSTALLARE
----------------------------------------------------------------------------
Nella directory /install trovate due script di shell, bukowskirc e bindrc.

Il primo, bukowskirc, si occupa di creare la directory /tmp/bukowski/outbox,
settare smtp.properties e pop3.properties nella home dell'utente,
e creare due account pop di prova, paperinik e nonnapapera, con il
relativo file passwd.properties nella directory /tmp/bukowski.

Il secondo, bindrc, configura bind per risolvere il dominio paperopoli.net
e la zona inversa 0.168.192.in-addr.arpa.
Copia i file di configurazione paperopoli.net.conf e 192.168.0.conf 
nella directory /var/named, oltre a named.conf nella directory /etc.


IMPOSTAZIONI DA EFFETTUARE A MANO
----------------------------------------------------------------------------
Vanno impostati a mano il nome del dns predefinito di sistema, in
/etc/resolv.conf, e, opzionalmente, le porte su cui gireranno i servizi
pop3 e smtp, in ~/pop3.properties e ~/smtp.properties (default,
rispettivamente, 110 e 25).


AVVIO DEL SERVER
----------------------------------------------------------------------------
Il server e' fornito in due archivi jar che potete trovare nella
directory /jar.
Per avviarli basta dare da console il comando

java -jar bukowski.jar

e

java -jar pop3.jar


----------------------------------------------------------------------------
                                              last revision 05-03-2004 10:40