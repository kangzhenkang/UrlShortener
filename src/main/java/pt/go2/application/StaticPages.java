package pt.go2.application;

import java.io.IOException;
import java.util.Calendar;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;

import pt.go2.fileio.Configuration;
import pt.go2.fileio.EmbeddedFiles;
import pt.go2.fileio.ErrorPages;
import pt.go2.response.AbstractResponse;
import pt.go2.response.RedirectResponse;
import pt.go2.storage.HashKey;
import pt.go2.storage.KeyValueStore;
import pt.go2.storage.Uri;

/**
 * Handles server requests
 */
class StaticPages extends RequestHandler {

    final Calendar calendar = Calendar.getInstance();

    final KeyValueStore ks;
    final EmbeddedFiles files;

    public StaticPages(final Configuration config, ErrorPages errors, KeyValueStore ks, EmbeddedFiles res) {
        super(config, errors);

        this.ks = ks;
        this.files = res;
    }

    /**
     * Handle request, parse URI filename from request into page resource
     *
     * @param
     *
     * @exception IOException
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse exchange) {

        final String requested = getRequestedFilename(request.getRequestURI());

        if (requested.length() == HashKey.LENGTH) {

            handleShortenedUrl(request, exchange, requested);

            return;
        }

        if (!config.getValidDomains().isEmpty() && request.getMethod().equals(HttpMethod.GET)) {

            // if its not a shortened URL that was requested, make sure
            // the prefered name is being used (ie www.go2.pt vs go2.pt)

            final String host = request.getHeader(AbstractResponse.REQUEST_HEADER_HOST).toLowerCase();

            final String preffered = config.getValidDomains().get(0);

            if (!host.equals(preffered)) {

                final String redirect = requested.startsWith("/") ? preffered + requested : preffered + "/" + requested;

                reply(request, exchange, new RedirectResponse(redirect, HttpStatus.MOVED_PERMANENTLY_301), true);

                return;
            }
        }

        final AbstractResponse response = files.getFile(requested);

        if (response == null) {
            reply(request, exchange, ErrorPages.Error.PAGE_NOT_FOUND, true);
        } else {
            reply(request, exchange, response, true);
        }
    }

    private void handleShortenedUrl(HttpServletRequest request, HttpServletResponse exchange, final String requested) {

        final Uri uri = ks.get(new HashKey(requested));

        if (uri == null) {
            reply(request, exchange, ErrorPages.Error.PAGE_NOT_FOUND, true);
            return;
        }

        switch (uri.health()) {
        case PHISHING:
            reply(request, exchange, ErrorPages.Error.PHISHING, true);
            break;
        case OK:
            reply(request, exchange, new RedirectResponse(uri.toString(), config.getRedirect()), true);
            break;
        case MALWARE:
            reply(request, exchange, ErrorPages.Error.MALWARE, true);
            break;
        default:
            reply(request, exchange, ErrorPages.Error.PAGE_NOT_FOUND, true);
        }
    }

    /**
     * Parse requested filename from URI
     *
     * @param path
     *
     * @return Requested filename
     */
    private String getRequestedFilename(String path) {

        // split into tokens

        if (path.isEmpty() || "/".equals(path)) {
            return "/";
        }

        final int idx = path.indexOf("/", 1);

        return idx == -1 ? path.substring(1) : path.substring(1, idx);
    }
}
