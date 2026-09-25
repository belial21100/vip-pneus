package fr.vippneus.intervention

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

/**
 * Documents clients fictifs reproduisant la mise en page des vrais (positions des étiquettes),
 * avec des données inventées.
 */
object FakeDocs {
    private val PAGE = PDRectangle(595f, 842f)

    private class W(val cs: PDPageContentStream) {
        /** [top] : haut du texte depuis le haut de la page, comme relevé sur les originaux. */
        fun t(x: Float, top: Float, size: Float, s: String) {
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA, size)
            cs.newLineAtOffset(x, PAGE.height - top - size * 0.8f)
            cs.showText(s)
            cs.endText()
        }

        fun hLine(y: Float, x0: Float, x1: Float) {
            cs.moveTo(x0, PAGE.height - y)
            cs.lineTo(x1, PAGE.height - y)
            cs.stroke()
        }

        fun vLine(x: Float, y0: Float, y1: Float) {
            cs.moveTo(x, PAGE.height - y0)
            cs.lineTo(x, PAGE.height - y1)
            cs.stroke()
        }
    }

    private fun write(file: File, block: W.() -> Unit) {
        PDDocument().use { doc ->
            val page = PDPage(PAGE)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { W(it).block() }
            doc.save(file)
        }
    }

    fun manuloc(file: File) = write(file) {
        t(45.4f, 78.2f, 24f, "Bon de commande")
        t(402.2f, 75.5f, 8f, "Date :")
        t(429.4f, 76.2f, 7f, "01/09/2026")
        t(31f, 105.9f, 8f, "Adresse de livraison :")
        t(31f, 118.2f, 7f, "ENTREPOT TEST LOGISTIQUE")
        t(31f, 126.6f, 7f, "Rue des Essais")
        t(31f, 151.8f, 7f, "02000   LAON")
        t(31f, 160.2f, 7f, "Tél:0300000000")
        t(31f, 168.6f, 7f, "Fax:0300000001")
        t(304.6f, 112.2f, 12f, "FOURNISSEUR TEST")
        t(304.6f, 169.8f, 12f, "57000   METZ")
        t(31f, 193.5f, 8f, "Adresse de facturation :")
        t(31f, 206.6f, 6f, "LOC TEST")
        t(31f, 215.0f, 6f, "5 RUE DE LA LOCATION")
        t(31f, 223.4f, 6f, "ZAC DES TESTS")
        t(31f, 240.2f, 6f, "51100   REIMS CEDEX 2")
        t(144.5f, 193.5f, 8f, "Adresse envoi facture :")
        t(144.5f, 206.6f, 6f, "LOC TEST")
        t(144.5f, 215.0f, 6f, "Service comptabilité")
        t(144.5f, 240.2f, 6f, "57000   METZ CEDEX 02")
        t(287.5f, 220.6f, 10f, "Référence à rappeler obligatoirement")
        t(287.5f, 232.6f, 10f, "N°")
        t(311.5f, 231.0f, 12f, "7654321AOM/F/TEST")
        t(31f, 264.0f, 10f, "Technicien : DUPONT Jean")
        t(290f, 250.4f, 12f, "OM n°  1111111  Engin n° M   123456 (  123456)")
        t(340f, 264.8f, 12f, "S4.5FT")
        t(31f, 296.6f, 10f, "Veuillez exécuter par cet ordre la fourniture de")
        t(120f, 328.2f, 7.6f, "LIVRAISON ET POSE - SELON DEVIS N°1000 DU 01/09/26")
        t(120f, 339.5f, 7.6f, "- 2 BD TREL 22X12X16 SM NM AVANT")
        t(120f, 350.7f, 7.6f, "- 2 BD TREL 18X8X12 1/8 GL NM ARRIERE")
        t(25.2f, 771.5f, 8f, "Conditions générales d'achats disponibles sur https://www.manuloc.fr/conditions-generales/")
    }

    fun continental(file: File) = write(file) {
        t(345f, 21f, 14f, "Mobile Service")
        t(457f, 20f, 14f, "900000001")
        t(33f, 45.5f, 8f, "Rendez-vous:")
        t(116f, 45.5f, 8f, "MANUTENTION TEST")
        t(33f, 56.5f, 8f, "Utilisateur:")
        t(116f, 56.5f, 8f, "(81) CLIENT TEST")
        t(249f, 57.5f, 8f, "Reçu par:")
        t(315f, 57.5f, 8f, "Agent TEST / 00")
        t(33f, 89.5f, 8f, "N° de flotte")
        t(116f, 89.5f, 8f, "12345")
        t(33f, 111.5f, 8f, "Modèle:")
        t(116f, 111.5f, 8f, "Autre/E16")
        t(33f, 122.5f, 8f, "Immatriculation")
        t(116f, 122.5f, 8f, "ABC123DEF456")
        t(35f, 158.3f, 10f, "Localisation:")
        t(35f, 172.5f, 8f, "10 rue des Tests ZAC Exemple, 02100")
        t(35f, 184.5f, 8f, "Plateforme Test")
        t(35f, 208.4f, 8f, "02100 Saint-Quentin")
        t(242f, 208.5f, 8f, "Fiche de")
        t(35f, 220.5f, 8f, "49.000000, 3.000000")
        t(35f, 232.5f, 8f, "N° de tél du chauffeur:")
        t(36f, 283.3f, 10f, "Pneus démontés")
        t(40.9f, 322.4f, 8f, "1LI")
        t(60f, 323.0f, 7f, "COMPETITOR SOLID 8\"")
        t(40.3f, 337.4f, 8f, "1RI")
        t(60f, 338.0f, 7f, "COMPETITOR SOLID 8\"")
        t(34f, 451.7f, 8f, "Facture à envoyer à:")
        t(34f, 460.9f, 8f, "Continental France / Conti360°")
        t(36f, 528.3f, 10f, "Pneus montés")
        t(43.9f, 566.4f, 8f, "1LI")
        t(63f, 567.0f, 7f, "180/70-8/4.33 SC20+ SIT 125 A5/4.33R8(18X7-8)")
        t(230.1f, 566.4f, 8f, "Neuf")
        t(43.3f, 581.4f, 8f, "1RI")
        t(63f, 582.0f, 7f, "180/70-8/4.33 SC20+ SIT 125 A5/4.33R8(18X7-8)")
        t(230.1f, 581.4f, 8f, "Neuf")
    }

    fun interfit(file: File) = write(file) {
        t(242.5f, 43.3f, 11.7f, "Feuille de tâche")
        t(22f, 105.4f, 7.8f, "Date de création")
        t(84.9f, 105.4f, 7.8f, "01/09/2026")
        t(412.7f, 105.4f, 7.8f, "ID")
        t(486.5f, 105.4f, 7.8f, "1234567")
        t(22f, 119.2f, 7.8f, "Livrer à")
        t(136.5f, 119.2f, 7.8f, "ESAT DU PARC (VILLE-TEST")
        t(136.5f, 128.7f, 7.8f, "54000)")
        t(295.3f, 119.2f, 7.8f, "Facturer la")
        t(368.4f, 119.2f, 7.8f, "CLIENT - TEST")
        t(22f, 142.4f, 7.8f, "Adresse du site de livraison")
        t(136.5f, 142.4f, 7.8f, "12 RUE DES LILAS, VILLE-TEST,")
        t(136.5f, 152.0f, 7.8f, "54000, FRANCE")
        t(22f, 218.1f, 7.8f, "Marque")
        t(22f, 227.6f, 7.8f, "TOYOTA")
        t(22f, 299.9f, 7.8f, "Instructions spéciales")
        t(22f, 313.2f, 7.8f, "Horamètre 2000h")
        t(22f, 365.6f, 7.8f, "Torque:")
        t(22f, 375.1f, 7.8f, "Front Left = 159 Nm (+/- 39 Nm)")
        t(347.2f, 303.4f, 7.8f, "Couple de serrage")
        t(23.2f, 444.0f, 7.8f, "Emplacement de la franchise")
        t(231.3f, 444.0f, 7.8f, "Date prévue")
        t(426.8f, 444.0f, 7.8f, "Commentaires")
        t(22f, 475.3f, 7.8f, "Lecture du compteur")
        t(231.3f, 476.3f, 7.8f, "Monteur")
        t(22f, 496.7f, 7.8f, "Heure d'arrivée (hh:mm)")
        t(231.3f, 496.7f, 7.8f, "Heure de départ")
        t(22f, 516.8f, 7.8f, "Date terminée")
        t(231.3f, 516.2f, 7.8f, "Durée")
        t(22f, 561.5f, 7.8f, "Date")
        t(22f, 578.7f, 7.8f, "Reçu par")
        listOf(470.9f, 490.7f, 511.1f, 533.2f).forEach { hLine(it, 10.2f, 422.8f) }
        listOf(128.0f, 226.4f, 318.0f, 422.1f).forEach { vLine(it, 470.9f, 533.2f) }
    }

    fun other(file: File) = write(file) {
        t(40f, 60f, 18f, "Devis D-2026-001")
        t(40f, 100f, 10f, "Client : Exemple SA")
        t(40f, 120f, 10f, "Remplacement de deux roues avant")
    }
}
