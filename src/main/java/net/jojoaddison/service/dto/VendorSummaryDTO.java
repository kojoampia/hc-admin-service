package net.jojoaddison.service.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * The four figures above the console's vendor directory.
 *
 * <p>Two of them are counts the client could already get for itself — {@code activeContracts} and
 * {@code underReview} are what {@code GET /api/vendors?status.equals=…&size=1} reports in
 * {@code X-Total-Count}, which is how the message desk's tiles work. They are here anyway so the
 * row of four arrives together: four requests to draw four tiles is four chances to half-render,
 * and the same argument already applies to {@link DashboardMetricsDTO}.
 *
 * <p>The other two are the reason this exists at all. {@code spendToDate} is a sum and
 * {@code categoryCount} a distinct count, and <strong>neither can be derived from a page</strong>.
 * A client totalling the twenty rows it happens to be showing would print a number that reads as
 * the whole book of business and is not — the fabricated-figure failure the in-browser mock was
 * removed for. Computing them server-side over the whole collection is the only form of these two
 * tiles that stays true once the directory is longer than one page.
 *
 * <p>Archived vendors are excluded from every figure, because the directory underneath excludes
 * them. A total that counted rows the table does not show would disagree with the table.
 *
 * <p><strong>There is no currency here because {@code Vendor} has no currency field.</strong>
 * Amounts are cedis by convention across this product, and the vendor record already says so in
 * its label rather than its data (<em>"Spend to date (GHS)"</em>). Returning a currency code this
 * service does not store would be inventing one, so the tile is labelled the same way the record
 * is; if vendors ever trade in more than one currency, the field belongs on {@code Vendor} first
 * and here second.
 *
 * @param spendToDate sum of {@code spend_to_date}; zero when nothing is recorded, never null
 * @param categoryCount distinct non-blank {@code category} values
 * @param activeContracts vendors whose status is {@code ACTIVE}
 * @param underReview vendors whose status is {@code UNDER_REVIEW} or {@code PENDING}
 */
public record VendorSummaryDTO(BigDecimal spendToDate, long categoryCount, long activeContracts, long underReview)
    implements Serializable {}
