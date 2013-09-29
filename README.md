Bukowski mail server
====================
An SPF-enabled mailserver (comes with a pop3 as well, just because it was fun writing it).
 
Old memories, for 9 years living only in:
http://unipdleague.cvs.sourceforge.net/viewvc/unipdleague/bukowski/

**This was a school project, dated back 2004. It's old, insecure, probably incomplete, terrible code to read. It's here only out of nostalgia :)**

On how to install, please check the [install](https://github.com/alexdown/bukowski/blob/master/install/readme.txt) link, or keep reading if you don't speak italian ;)


----------------------------------------------------------------------------

                                                   Java Bukowski mail server
                                       (with SPF sender verification system)

----------------------------------------------------------------------------

(c) 2004 by the Bukowski Team
Basso Alessio, Di Giovinazzo Matteo, Penzo Peter
(strictly in alphabetical order)


INSTALL
----------------------------------------------------------------------------
There are two bash scripts under /install, bukowskirc and bindrc.

The former, bukowskirc, takes care of create the direcroty /tmp/bukowski/outbox,
setting smtp.properties and pop3.properties under user's homedir,
and create two test pop accounts, paperinik and nonnapapera (italian for "Grandma Duck"),
with the correspondent passwd.properties under /tmp/bukowski.

The latter, bindrc, configures bind to resolve the domain paperopoli.net (italian for "duckburg.net")
and the reverse zone 0.168.192.in-addr.arpa.
It'll copy the config files paperopoli.net.conf and 192.168.0.conf 
under /var/named, as well as named.conf under /etc.


MANUAL SETTINGS
----------------------------------------------------------------------------
The system default dns (in /etc/resolv.conf) must be manually set, as well as - optionally -
the tcp ports for the pop3 and smtp services, in ~/pop3.properties e ~/smtp.properties 
(defaults are 110 and 25).


SERVER STARTUP
----------------------------------------------------------------------------
Server is distributed in two jar archives under /jar.
To start them, just run:

java -jar bukowski.jar

and

java -jar pop3.jar


----------------------------------------------------------------------------
                                              last revision 05-03-2004 10:40