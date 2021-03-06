package pt.go2.external;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.go2.fileio.WhiteList;
import pt.go2.storage.Uri;
import pt.go2.storage.Uri.Health;

public class UrlHealth {

    private static final Logger LOGGER = LogManager.getLogger();

    private final PhishLocalCache banned;
    private final WhiteList whitelist;
    private final SafeBrowsingLookup sbl;

    public UrlHealth(WhiteList whitelist, PhishLocalCache banned, SafeBrowsingLookup sbl) {
        this.banned = banned;
        this.whitelist = whitelist;
        this.sbl = sbl;
    }

    public void test(Set<Uri> uris) {

        final List<Uri> lookuplist = new ArrayList<>();

        for (final Uri uri : uris) {

            if (uri.health() != Health.OK) {
                continue;
            }

            test(uri, false);

            if (uri.health() != Health.OK) {
                LOGGER.trace(uri.toString() + " - " + uri.health().toString());

            } else if (sbl.canUseSafeBrowsingLookup()) {

                // remember files that still need to be checked

                lookuplist.add(uri);
            }
        }

        // prepare list for safebrowsing lookup

        sbl.safeBrowsingLookup(lookuplist);
    }

    public void test(Uri uri, boolean useSafeBrowsing) {

        if (this.whitelist.contains(uri.domain())) {
            uri.setHealth(Health.OK);
            return;
        }

        // check if Phishing

        if (banned.isBanned(uri)) {
            uri.setHealth(Uri.Health.PHISHING);
            LOGGER.info("Caugh phishing: " + uri);
            return;
        }

        if (useSafeBrowsing && sbl.canUseSafeBrowsingLookup()) {
            sbl.safeBrowsingLookup(uri);
        }
    }
}
