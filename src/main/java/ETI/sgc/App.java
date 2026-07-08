package ETI.sgc;

import ETI.sgc.config.AppConfig;
import ETI.sgc.config.Database;
import ETI.sgc.controller.*;
import ETI.sgc.audit.AuditDAO;
import ETI.sgc.dao.*;
import ETI.sgc.document.DocumentController;
import ETI.sgc.document.DocumentDAO;
import ETI.sgc.document.DocumentService;
import ETI.sgc.error.ApiException;
import ETI.sgc.error.ErrorResponse;
import ETI.sgc.mobile.MobileController;
import ETI.sgc.mobile.PushTokenDAO;
import ETI.sgc.mobile.RefreshTokenDAO;
import ETI.sgc.mobile.RefreshTokenService;
import ETI.sgc.payment.PaymentController;
import ETI.sgc.payment.PaymentDAO;
import ETI.sgc.payment.PaymentService;
import ETI.sgc.payment.providers.EpaycoPaymentProvider;
import ETI.sgc.payment.providers.MercadoPagoPaymentProvider;
import ETI.sgc.payment.providers.WompiPaymentProvider;
import ETI.sgc.payment.receipt.PaymentReceiptService;
import ETI.sgc.payment.transfer.PaymentTransferController;
import ETI.sgc.payment.transfer.PaymentTransferDAO;
import ETI.sgc.payment.transfer.PaymentTransferService;
import ETI.sgc.security.JwtUtil;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.staticfiles.Location;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Map;
import java.util.Set;

public class App {

    private static final Set<String> PUBLIC_API_PATHS = Set.of(
            "/api/login",
            "/api/mobile/v1/login",
            "/api/mobile/v1/refresh"
    );

    public static void main(String[] args) {
        AppConfig appConfig = AppConfig.load();
        JwtUtil.configure(appConfig);
        Database.init(appConfig);

        UsuarioDAO usuarioDao = new UsuarioDAO(Database.jdbi);
        ConceptoDAO conceptoDao = new ConceptoDAO(Database.jdbi);
        ValidacionConceptoDAO validacionDao = new ValidacionConceptoDAO(Database.jdbi);
        ObligacionDAO obligacionDao = new ObligacionDAO(Database.jdbi);
        MovimientoDAO movimientoDao = new MovimientoDAO(Database.jdbi);
        IntegranteDAO integranteDao = new IntegranteDAO(Database.jdbi);
        EntidadDAO entidadDao = new EntidadDAO(Database.jdbi);
        PatrocinadorDAO patrocinadorDao = new PatrocinadorDAO(Database.jdbi);
        ProveedorDAO proveedorDao = new ProveedorDAO(Database.jdbi);
        PaymentDAO paymentDAO = new PaymentDAO(Database.jdbi);
        PaymentTransferDAO paymentTransferDAO = new PaymentTransferDAO(Database.jdbi);
        DocumentDAO documentDAO = new DocumentDAO(Database.jdbi);
        AuditDAO auditDAO = new AuditDAO(Database.jdbi);
        RefreshTokenDAO refreshTokenDAO = new RefreshTokenDAO(Database.jdbi);
        PushTokenDAO pushTokenDAO = new PushTokenDAO(Database.jdbi);
        PaymentReceiptService receiptService = new PaymentReceiptService(appConfig);

        bootstrapAdminIfEnabled(appConfig, usuarioDao);

        Javalin app = Javalin.create(config -> {
            config.http.defaultContentType = "application/json";

            if (appConfig.getBoolean("WEPA_PUBLIC_UPLOADS_ENABLED", false)) {
                config.staticFiles.add("uploads", Location.EXTERNAL);
            }
        });

        configureErrors(app);
        configureSecurity(app);

        app.get("/", ctx -> ctx.json(Map.of(
                "app", "WEPA backend",
                "status", "ok",
                "environment", appConfig.get("APP_ENV", "local")
        )));
        app.get("/health", ctx -> ctx.json(Map.of(
                "status", "ok",
                "service", "wepa-backend"
        )));

        Runtime.getRuntime().addShutdownHook(new Thread(Database::shutdown));

        new AuthController(usuarioDao).routes(app);
        new UsuarioController(usuarioDao).routes(app);
        new AdministracionController(conceptoDao).routes(app);
        new FinanzasController(Database.jdbi, obligacionDao, movimientoDao, validacionDao, appConfig).routes(app);
        new IntegranteController(integranteDao, usuarioDao).routes(app);
        new EntidadController(entidadDao).routes(app);
        new PatrocinadorController(patrocinadorDao).routes(app);
        new ProveedorController(proveedorDao).routes(app);

        PaymentService paymentService = new PaymentService(
                paymentDAO,
                obligacionDao,
                auditDAO,
                receiptService,
                new WompiPaymentProvider(appConfig),
                new EpaycoPaymentProvider(appConfig),
                new MercadoPagoPaymentProvider(appConfig)
        );
        new PaymentController(paymentService).routes(app);

        PaymentTransferService paymentTransferService = new PaymentTransferService(paymentTransferDAO, appConfig);
        new PaymentTransferController(paymentTransferService, usuarioDao).routes(app);

        DocumentService documentService = new DocumentService(documentDAO, auditDAO, appConfig);
        new DocumentController(documentService).routes(app);

        RefreshTokenService refreshTokenService = new RefreshTokenService(refreshTokenDAO, appConfig);
        new MobileController(usuarioDao, integranteDao, obligacionDao, movimientoDao, documentService, refreshTokenService, pushTokenDAO).routes(app);

        int port = appConfig.getInt("APP_PORT", 8080);
        app.start(port);

        System.out.println("ERP WEPA listo en puerto " + port);
        System.out.println("Entorno activo: " + appConfig.get("APP_ENV", "local"));
    }

    private static void configureSecurity(Javalin app) {
        app.before("/api/*", App::requireJwt);
    }

    private static void requireJwt(Context ctx) {
        String path = ctx.path();
        if (PUBLIC_API_PATHS.contains(path) || path.startsWith("/api/payments/webhooks/")) {
            return;
        }

        String auth = ctx.header("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new ApiException(401, "Token requerido");
        }

        try {
            var claims = JwtUtil.validar(auth.substring("Bearer ".length()).trim());
            ctx.attribute("rol", claims.get("rol"));
            ctx.attribute("username", claims.getSubject());
            ctx.attribute("usuario_id", claims.get("uid"));
            ctx.attribute("integrante_id", claims.get("integrante_id"));
            ctx.attribute("permisos", claims.get("permisos"));
        } catch (Exception e) {
            throw new ApiException(401, "Token invalido o expirado");
        }
    }

    private static void configureErrors(Javalin app) {
        app.exception(ApiException.class, (e, ctx) ->
                ctx.status(e.getStatus()).json(new ErrorResponse(e.getMessage(), ctx.path()))
        );

        app.exception(BadRequestResponse.class, (e, ctx) ->
                ctx.status(400).json(new ErrorResponse(e.getMessage(), ctx.path()))
        );

        app.exception(NumberFormatException.class, (e, ctx) ->
                ctx.status(400).json(new ErrorResponse("Parametro numerico invalido", ctx.path()))
        );

        app.exception(NotFoundResponse.class, (e, ctx) ->
                ctx.status(404).json(new ErrorResponse("Recurso no encontrado", ctx.path()))
        );

        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            ctx.status(500).json(new ErrorResponse("Error interno del servidor", ctx.path()));
        });
    }

    private static void bootstrapAdminIfEnabled(AppConfig appConfig, UsuarioDAO usuarioDao) {
        if (!appConfig.getBoolean("WEPA_BOOTSTRAP_ADMIN_ENABLED", true)) {
            return;
        }

        String username = appConfig.get("WEPA_BOOTSTRAP_ADMIN_USER", "admin");
        if (usuarioDao.buscarPorUsername(username) != null) {
            return;
        }

        String password = appConfig.get("WEPA_BOOTSTRAP_ADMIN_PASSWORD", "admin123");
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        usuarioDao.crearUsuarioCompleto(
                username, hash, "ADMIN", "0000000000",
                "Administrador", "Sistema", "admin@sgc.local",
                "0000000000", "Sistema"
        );

        System.err.println("ADVERTENCIA: usuario admin bootstrap creado. Cambiar credenciales inmediatamente.");
    }
}
