package mx.tallermeco.address;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/addresses/postal-codes")
public class PostalController {
    private final PostalCatalog catalog;

    public PostalController(PostalCatalog catalog) { this.catalog = catalog; }

    @GetMapping("/{code}")
    public PostalCatalog.Address lookup(@PathVariable String code) { return catalog.lookup(code); }
}
