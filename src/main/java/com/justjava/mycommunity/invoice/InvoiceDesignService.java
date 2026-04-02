package com.justjava.mycommunity.invoice;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InvoiceDesignService {

    @Autowired
    private InvoiceDesignRepository designRepository;

    @Transactional
    public DesignDTO.Response saveDesign(DesignDTO.Request request) {
        InvoiceDesign design = new InvoiceDesign();
        design.setDesignName(request.getDesignName());
        design.setDesignHtml(request.getDesignHtml());

        design = designRepository.save(design);
        return DesignDTO.Response.fromEntity(design);
    }

    @Transactional(readOnly = true)
    public List<DesignDTO.Response> getAllDesigns() {
        List<InvoiceDesign> designs = designRepository.findAllByOrderByCreatedAtDesc();
        return designs.stream()
                .map(DesignDTO.Response::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DesignDTO.Response getDesignById(Long id) {
        InvoiceDesign design = designRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Design not found with id: " + id));
        return DesignDTO.Response.fromEntity(design);
    }

    @Transactional
    public DesignDTO.Response updateDesign(Long id, DesignDTO.Request request) {
        InvoiceDesign design = designRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Design not found with id: " + id));

        design.setDesignName(request.getDesignName());
        design.setDesignHtml(request.getDesignHtml());

        design = designRepository.save(design);
        return DesignDTO.Response.fromEntity(design);
    }

    @Transactional
    public void deleteDesign(Long id) {
        if (!designRepository.existsById(id)) {
            throw new RuntimeException("Design not found with id: " + id);
        }
        designRepository.deleteById(id);
    }
}
