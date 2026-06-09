package swdchatbox.system.document.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import swdchatbox.system.document.entity.Document;

@SpringBootTest
class DocumentKeywordSearchTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void keywordSearchShouldNotFail() {
        Specification<Document> spec = Specification
                .where(DocumentSpecifications.keywordLike("hehee"));
        documentRepository.findAll(spec, PageRequest.of(0, 100));
    }
}
