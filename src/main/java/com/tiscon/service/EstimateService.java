package com.tiscon.service;

import com.tiscon.code.OptionalServiceType;
import com.tiscon.code.PackageType;
import com.tiscon.dao.EstimateDao;
import com.tiscon.domain.Customer;
import com.tiscon.domain.CustomerOptionService;
import com.tiscon.domain.CustomerPackage;
import com.tiscon.dto.UserOrderDto;
import com.tiscon.form.UserOrderForm;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 引越し見積もり機能において業務処理を担当するクラス。
 *
 * @author Oikawa Yumi
 */
@Service
public class EstimateService {

    /** 引越しする距離の1 kmあたりの料金[円] */
    private static final int PRICE_PER_DISTANCE = 100;

    private final EstimateDao estimateDAO;

    /**
     * コンストラクタ。
     *
     * @param estimateDAO EstimateDaoクラス
     */
    public EstimateService(EstimateDao estimateDAO) {
        this.estimateDAO = estimateDAO;
    }

    /**
     * 見積もり依頼をDBに登録する。
     *
     * @param dto 見積もり依頼情報
     */
    @Transactional
    public void registerOrder(UserOrderDto dto) {
        Customer customer = new Customer();
        BeanUtils.copyProperties(dto, customer);
        estimateDAO.insertCustomer(customer);

        if (dto.getWashingMachineInstallation()) {
            CustomerOptionService washingMachine = new CustomerOptionService();
            washingMachine.setCustomerId(customer.getCustomerId());
            washingMachine.setServiceId(OptionalServiceType.WASHING_MACHINE.getCode());
            estimateDAO.insertCustomersOptionService(washingMachine);
        }

        List<CustomerPackage> packageList = new ArrayList<>();

        packageList.add(new CustomerPackage(customer.getCustomerId(), PackageType.BOX.getCode(), dto.getBox()));
        packageList.add(new CustomerPackage(customer.getCustomerId(), PackageType.BED.getCode(), dto.getBed()));
        packageList.add(new CustomerPackage(customer.getCustomerId(), PackageType.BICYCLE.getCode(), dto.getBicycle()));
        packageList.add(new CustomerPackage(customer.getCustomerId(), PackageType.WASHING_MACHINE.getCode(), dto.getWashingMachine()));
        estimateDAO.batchInsertCustomerPackage(packageList);
    }

    public Integer getBasicPrice(UserOrderDto dto){
        //距離を出す
        String distance = dto.getDistance();
        Double distance1 = Double.parseDouble(distance);
        // 小数点以下を切り捨てる
        int distanceInt = (int) Math.floor(distance1);

        // 距離当たりの料金を算出する
        int priceForDistance = distanceInt * PRICE_PER_DISTANCE;
        return priceForDistance;
    }
    public Integer getTruckPrice(UserOrderDto dto){
        //箱の数
        int boxes = getBoxForPackage(dto.getBox(), PackageType.BOX)
                + getBoxForPackage(dto.getBed(), PackageType.BED)
                + getBoxForPackage(dto.getBicycle(), PackageType.BICYCLE)
                + getBoxForPackage(dto.getWashingMachine(), PackageType.WASHING_MACHINE);

        // 箱に応じてトラックの種類が変わり、それに応じて料金が変わるためトラック料金を算出する。
//        int pricePerTruck = estimateDAO.getPricePerTruck(boxes);
        int pricePerTruck=0;
        int y = boxes / 200;
        int z = boxes % 200;

        if (z > 80) {
            y=y+1;
            pricePerTruck = y * 50000;
        };

        if (z <= 80) {
            pricePerTruck = 50000 * y +30000;
        };
        return  pricePerTruck;
    }
    public Integer getOptionPrice(UserOrderDto dto) {
        // オプションサービスの料金を算出する。
        int priceForOptionalService = 0;

        if (dto.getWashingMachineInstallation()) {
            priceForOptionalService = estimateDAO.getPricePerOptionalService(OptionalServiceType.WASHING_MACHINE.getCode());
        }
        return priceForOptionalService;
    }
    /**
     * 見積もり依頼に応じた概算見積もりを行う。
     *
     * @param dto 見積もり依頼情報
     * @return 概算見積もり結果の料金
     */
    public Integer getPrice(UserOrderDto dto) {
        //季節を考慮
        String movingdate = dto.getMovingdate();
        int movingdate1 = Integer.parseInt(movingdate);
        double n = 1;
        if (movingdate1==3 || movingdate1==4) {
            n = 1.5;
        } else if(movingdate1==9) {
            n = 1.2;
        }
        int priceForDistance = getBasicPrice(dto);
        int pricePerTruck = getTruckPrice(dto);
        int priceForOptionalService = getOptionPrice(dto);

        Double price = (priceForDistance + pricePerTruck) * n+ priceForOptionalService;
        return price.intValue();
    }

    /**
     * 荷物当たりの段ボール数を算出する。
     *
     * @param packageNum 荷物数
     * @param type       荷物の種類
     * @return 段ボール数
     */
    private int getBoxForPackage(int packageNum, PackageType type) {
        return packageNum * estimateDAO.getBoxPerPackage(type.getCode());
    }
}