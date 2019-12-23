package com.lcn.coolweather;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NavUtils;
import androidx.fragment.app.Fragment;

import com.lcn.coolweather.db.City;
import com.lcn.coolweather.db.County;
import com.lcn.coolweather.db.Province;
import com.lcn.coolweather.util.HttpUtil;
import com.lcn.coolweather.util.Utility;

import org.jetbrains.annotations.NotNull;
import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * 程序入口:
 * onCreateView
 * onActivityCreated
 * onItemClick
 * onClick
 *
 * 标志位的更改,什么时候需要创建标志位?标志位什么时候会变动?
 * 进度条的开关?什么时候开?什么时候关?老是记得开,忘记关,有没有什么办法?
 */

public class ChooseAreaFragment extends Fragment {

    //创建省,市,区的层级标志,为了区分listview所处的层级.
    private static final int LEVEL_PROVINCE = 0;
    private static final int LEVEL_CITY = 1;
    private static final int LEVEL_COUNTY = 2;

    private TextView mTitle;
    private Button mBackButton;
    private ListView mListView;

    private List<String> mDataList = new ArrayList<>();
    private ArrayAdapter<String> mAdapter;
    private List<Province> mProvinceList;
    private List<City> mCityList;
    private int mCurrentLevel;
    private Province mSelectedProvince;
    private City mSelectedCity;
    private List<County> mCountyList;
    private ProgressBar mProgressBar;

    //定义到服务器查找的层级类型
    enum Type{
        province,
        city,
        county
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container
            , @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_arex, container, false);
        mProgressBar = view.findViewById(R.id.progress_bar);
        mTitle = view.findViewById(R.id.title_text);
        mBackButton = view.findViewById(R.id.back_button);
        mListView = view.findViewById(R.id.list_view);

        mAdapter = new ArrayAdapter<>(Objects.requireNonNull(getActivity()),
                android.R.layout.simple_list_item_1, mDataList);
        mListView.setAdapter(mAdapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mListView.setOnItemClickListener((parent, view, position, id) -> {
            if (mCurrentLevel == LEVEL_PROVINCE) {
                mSelectedProvince = mProvinceList.get(position);
                queryCities();
            } else if (mCurrentLevel == LEVEL_CITY) {
                mSelectedCity = mCityList.get(position);
                queryCounty();
            } else if (mCurrentLevel == LEVEL_COUNTY) {
                String weatherId = mCountyList.get(position).getWeatherId();
                //该fragment被填充到了MainActivity和WeatherActivity里面处理不同的逻辑,需要不同对待.
                if (getActivity() instanceof MainActivity) {
                    Intent intent = new Intent(getActivity(), WeatherActivity.class);
                    intent.putExtra("weather_id", weatherId);
                    startActivity(intent);
                    //添加这一句是为了什么?为了不显示城镇列表直接退出应用?想不明白.
                    getActivity().finish();
                } else if (getActivity() instanceof WeatherActivity) {
                    WeatherActivity weatherActivity = (WeatherActivity) getActivity();
                    weatherActivity.drawerLayout.closeDrawers();
                    weatherActivity.swipeRefresh.setRefreshing(true);
                    weatherActivity.requestWeather(weatherId);
                }
            }
        });
        mBackButton.setOnClickListener(v -> {
            if (mCurrentLevel == LEVEL_COUNTY) {
                queryCities();
            } else if (mCurrentLevel == LEVEL_CITY) {
                queryProvinces();
            }
        });
        //首先去获取省份列表.
        queryProvinces();
    }

    private void queryCounty() {
        //更新UI
        managerActionbar(mSelectedCity.getCityName(), View.VISIBLE);
        //先从数据库中去查询.
        mCountyList = DataSupport.where("cityid=?"
                , String.valueOf(mSelectedCity.getId())).find(County.class);

        List<County> all = DataSupport.findAll(County.class);

        Log.d("lichengnan", all.size() + ", ");
        if (mCountyList.size() > 0) {
            mDataList.clear();
            for (int i = 0; i < mCountyList.size(); i++) {
                mDataList.add(mCountyList.get(i).getCountyName());
            }
            mAdapter.notifyDataSetChanged();
            mListView.setSelection(0);
            //更改标志位
            mCurrentLevel = LEVEL_COUNTY;
        } else {
            //再从网络上去查询
            String url = "http://guolin.tech/api/china/" + mSelectedProvince.getProvinceCode() + "/"
                    + mSelectedCity.getCityCode();
            Log.d("lichengnan", "url = " + url);
            queryFromServer(url, Type.county);
        }

    }

    /**
     * 查询城市信息,首先变更UI,然后从数据库中查找,然后从网路上查找.
     */
    private void queryCities() {
        //更新UI.
        managerActionbar(mSelectedProvince.getProvinecName(), View.VISIBLE);
        //先从数据库中去查询.
        mCityList = DataSupport.where("provinceid=?", String.valueOf(mSelectedProvince.getId())).find(City.class);
        if (mCityList.size() > 0) {
            mDataList.clear();
            for (int i = 0; i < mCityList.size(); i++) {
                mDataList.add(mCityList.get(i).getCityName());
            }
            mAdapter.notifyDataSetChanged();
            mListView.setSelection(0);
            //更新标志位.
            mCurrentLevel = LEVEL_CITY;
        } else {
            String url = "http://guolin.tech/api/china/" + mSelectedProvince.getProvinceCode();
            queryFromServer(url, Type.city);
        }
    }

    private void queryProvinces() {
        //这个调用后,应该显示的是主界面,所以标题需要更改,返回按钮需要隐藏.
        managerActionbar("中国", View.GONE);
        //先从数据库中查询数据.
        mProvinceList = DataSupport.findAll(Province.class);
        if (mProvinceList.size() > 0) {
            mDataList.clear();
            for (int i = 0; i < mProvinceList.size(); i++) {
                mDataList.add(mProvinceList.get(i).getProvinecName());
            }
            mAdapter.notifyDataSetChanged();
            //listview的选中置为第一个
            mListView.setSelection(0);
            //更改标志位
            mCurrentLevel = LEVEL_PROVINCE;
        } else {
            String url = "http://guolin.tech/api/china";
            queryFromServer(url, Type.province);
        }
    }

    private void managerActionbar(String title, int visibility) {
        mTitle.setText(title);
        mBackButton.setVisibility(visibility);
    }

    /**
     * 从服务器上查询信息.
     *
     * @param url
     * @param type
     */
    private void queryFromServer(String url, Type type) {
        //开始加载,显示进度条.
        showProgressDialog();

        HttpUtil.sendHttpRequest(url, new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                //加载失败,关进度条,提示失败.
                getActivity().runOnUiThread(() -> {
                    closeProgressDialog();
                    Toast.makeText(getActivity(), "加载失败...", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                });
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String responseText = Objects.requireNonNull(response.body()).string();
                boolean result = false;
                if (Type.province.equals(type)) {
                    result = Utility.handleProvinceResponse(responseText);
                } else if (Type.city.equals(type)) {
                    result = Utility.handleCityResponse(responseText, mSelectedProvince.getId());
                } else if (Type.county.equals(type)) {
                    result = Utility.handleCountyResponse(responseText, mSelectedCity.getId());
                }
                if (result) {
                    getActivity().runOnUiThread(() -> {
                        //首先关闭进度条.
                        closeProgressDialog();
                        Toast.makeText(getActivity(), "Success", Toast.LENGTH_SHORT).show();
                        //从数据库中重新查询数据
                        if (Type.province.equals(type)) {
                            queryProvinces();
                        } else if (Type.city.equals(type)) {
                            queryCities();
                        } else {
                            queryCounty();
                        }
                    });
                }
            }
        });
    }

    /**
     * 显示进度条
     */
    private void showProgressDialog() {
        if (!mProgressBar.isShown()) {
            mProgressBar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 关闭进度条
     */
    private void closeProgressDialog() {
        if (mProgressBar.isShown()) {
            mProgressBar.setVisibility(View.GONE);
        }
    }
}
