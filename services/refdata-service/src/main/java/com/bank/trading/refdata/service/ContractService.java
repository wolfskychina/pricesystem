package com.bank.trading.refdata.service;

import com.bank.trading.common.core.dto.ContractDTO;
import com.bank.trading.common.core.exception.BusinessException;
import com.bank.trading.refdata.entity.Contract;
import com.bank.trading.refdata.mapper.ContractMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContractService {

    private final ContractMapper contractMapper;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContractService.class);

    public ContractService(ContractMapper contractMapper) {
        this.contractMapper = contractMapper;
    }

    public List<ContractDTO> listActiveContracts() {
        return contractMapper.findAllActive().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ContractDTO getContractByCode(String code) {
        Contract contract = contractMapper.findByCode(code);
        if (contract == null) {
            throw new BusinessException(404, "Contract not found: " + code);
        }
        return toDTO(contract);
    }

    public ContractDTO getContractById(Long id) {
        Contract contract = contractMapper.findById(id);
        if (contract == null) {
            throw new BusinessException(404, "Contract not found: " + id);
        }
        return toDTO(contract);
    }

    public List<ContractDTO> listByExchange(String exchange) {
        return contractMapper.findByExchange(exchange).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ContractDTO createContract(ContractDTO dto) {
        Contract existing = contractMapper.findByCode(dto.getCode());
        if (existing != null) {
            throw new BusinessException(400, "Contract already exists: " + dto.getCode());
        }
        Contract contract = toEntity(dto);
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        if (contract.getStatus() == null) {
            contract.setStatus("ACTIVE");
        }
        contractMapper.insert(contract);
        return toDTO(contract);
    }

    public ContractDTO updateContract(Long id, ContractDTO dto) {
        Contract existing = contractMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(404, "Contract not found: " + id);
        }
        if (dto.getName() != null) existing.setName(dto.getName());
        if (dto.getExchange() != null) existing.setExchange(dto.getExchange());
        if (dto.getProduct() != null) existing.setProduct(dto.getProduct());
        if (dto.getMultiplier() != null) existing.setMultiplier(dto.getMultiplier());
        if (dto.getTickSize() != null) existing.setTickSize(dto.getTickSize());
        if (dto.getMinQty() != null) existing.setMinQty(dto.getMinQty());
        if (dto.getListedDate() != null) existing.setListedDate(dto.getListedDate());
        if (dto.getExpiryDate() != null) existing.setExpiryDate(dto.getExpiryDate());
        if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        contractMapper.update(existing);
        return toDTO(existing);
    }

    private ContractDTO toDTO(Contract entity) {
        ContractDTO dto = new ContractDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setExchange(entity.getExchange());
        dto.setProduct(entity.getProduct());
        dto.setMultiplier(entity.getMultiplier());
        dto.setTickSize(entity.getTickSize());
        dto.setMinQty(entity.getMinQty());
        dto.setListedDate(entity.getListedDate());
        dto.setExpiryDate(entity.getExpiryDate());
        dto.setStatus(entity.getStatus());
        return dto;
    }

    private Contract toEntity(ContractDTO dto) {
        Contract entity = new Contract();
        entity.setId(dto.getId());
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setExchange(dto.getExchange());
        entity.setProduct(dto.getProduct());
        entity.setMultiplier(dto.getMultiplier());
        entity.setTickSize(dto.getTickSize());
        entity.setMinQty(dto.getMinQty());
        entity.setListedDate(dto.getListedDate());
        entity.setExpiryDate(dto.getExpiryDate());
        entity.setStatus(dto.getStatus());
        return entity;
    }
}
