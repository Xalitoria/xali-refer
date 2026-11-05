package mc.xalitoria.refer.paper.data.constants

object Permissions {
    /** `/refer info`, `ip`, `top` and `list` for oneself. */
    const val USE = "xalirefer.use"

    /** `/refer list` for another player. */
    const val LIST_OTHERS = "xalirefer.list.others"

    const val ADMIN = "xalirefer.admin"

    /** `/refer admin log`: the addresses players joined from. */
    const val LOG = "xalirefer.log"

    /** `/refer admin log remove` and `restore`: take a referral out of the count, and put it back. */
    const val REVIEW = "xalirefer.review"
}
