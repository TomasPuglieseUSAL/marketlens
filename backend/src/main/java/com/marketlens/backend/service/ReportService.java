package com.marketlens.backend.service;

import com.marketlens.backend.dto.CreateReportRequest;
import com.marketlens.backend.dto.ReportResponse;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.Report;
import com.marketlens.backend.entity.User;
import com.marketlens.backend.exception.ResourceNotFoundException;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.ReportRepository;
import com.marketlens.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final PublicationRepository publicationRepository;
    private final UserRepository userRepository;

    public ReportService(
        ReportRepository reportRepository,
        PublicationRepository publicationRepository,
        UserRepository userRepository) {

    this.reportRepository = reportRepository;
    this.publicationRepository = publicationRepository;
    this.userRepository = userRepository;
}

    @Transactional
    public ReportResponse create(
            Long publicationId,
            String username,
            CreateReportRequest request) {

        Publication publication = publicationRepository
            .findById(publicationId)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Publication not found: " + publicationId
                )
            );
        
        User user = userRepository
            .findByUsername(username)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                "Authenticated user not found: " + username
            )
        );

        Report report = new Report();
        
        report.setPublication(publication);
        report.setUser(user);
        report.setReason(request.reason());
        report.setDescription(request.description());

        Report savedReport = reportRepository.save(report);

        return toResponse(savedReport);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getByPublication(
            Long publicationId) {

        if (!publicationRepository.existsById(publicationId)) {
            throw new ResourceNotFoundException(
                "Publication not found: " + publicationId
            );
        }

        return reportRepository
            .findByPublicationIdOrderByCreatedAtDesc(publicationId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private ReportResponse toResponse(Report report) {
        
        return new ReportResponse(
            report.getId(),
            report.getPublication().getId(),
            report.getUser().getId(),
            report.getReason(),
            report.getDescription(),
            report.getCreatedAt()
        );
    }
}