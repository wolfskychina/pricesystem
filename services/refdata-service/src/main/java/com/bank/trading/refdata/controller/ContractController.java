package com.bank.trading.refdata.controller;

import com.bank.trading.common.core.dto.ContractDTO;
import com.bank.trading.common.core.result.Result;
import com.bank.trading.refdata.service.ContractService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/refdata/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    public Result<List<ContractDTO>> listActiveContracts() {
        return Result.success(contractService.listActiveContracts());
    }

    @GetMapping("/{code}")
    public Result<ContractDTO> getContractByCode(@PathVariable String code) {
        return Result.success(contractService.getContractByCode(code));
    }

    @GetMapping("/id/{id}")
    public Result<ContractDTO> getContractById(@PathVariable Long id) {
        return Result.success(contractService.getContractById(id));
    }

    @GetMapping("/exchange/{exchange}")
    public Result<List<ContractDTO>> listByExchange(@PathVariable String exchange) {
        return Result.success(contractService.listByExchange(exchange));
    }

    @PostMapping
    public Result<ContractDTO> createContract(@RequestBody ContractDTO dto) {
        return Result.success(contractService.createContract(dto));
    }

    @PutMapping("/{id}")
    public Result<ContractDTO> updateContract(@PathVariable Long id, @RequestBody ContractDTO dto) {
        return Result.success(contractService.updateContract(id, dto));
    }
}
